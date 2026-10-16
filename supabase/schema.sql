-- Portal Capture v1. Tour drafts are atomic documents; media and jobs have independent lifecycles.
create table if not exists public.portal_tours (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
 title text not null check(length(title) between 1 and 160), slug text unique not null,
 document jsonb not null, revision integer not null default 1 check(revision > 0),
 published_revision integer, updated_at timestamptz not null default now()
);
create table if not exists public.portal_revisions (
 tour_id uuid not null references public.portal_tours on delete cascade, revision integer not null,
 document jsonb not null, created_at timestamptz not null default now(), primary key(tour_id, revision)
);
create table if not exists public.portal_captures (
 id uuid primary key, owner_id uuid not null references auth.users on delete cascade,
 tour_id uuid not null references public.portal_tours on delete cascade, name text not null,
 manifest jsonb not null, status text not null default 'uploading' check(status in ('uploading','queued','processing','ready','needs-retake','failed','cancelled')),
 result jsonb, error text, attempts integer not null default 0, lease_until timestamptz, worker_id uuid,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index if not exists portal_tours_owner on public.portal_tours(owner_id);
create index if not exists portal_captures_owner on public.portal_captures(owner_id,tour_id);
create index if not exists portal_capture_queue on public.portal_captures(status,lease_until,created_at);
alter table public.portal_tours enable row level security;
alter table public.portal_revisions enable row level security;
alter table public.portal_captures enable row level security;
create policy tour_owner_read on public.portal_tours for select to authenticated using(owner_id = (select auth.uid()));
create policy tour_owner_insert on public.portal_tours for insert to authenticated with check(owner_id = (select auth.uid()) and published_revision is null and revision = 1);
create policy tour_owner_update on public.portal_tours for update to authenticated using(owner_id = (select auth.uid())) with check(owner_id = (select auth.uid()));
create policy revision_owner_read on public.portal_revisions for select to authenticated using(exists(select 1 from public.portal_tours t where t.id=tour_id and t.owner_id=(select auth.uid())));
create policy capture_owner_read on public.portal_captures for select to authenticated using(owner_id=(select auth.uid()));
create policy capture_owner_insert on public.portal_captures for insert to authenticated with check(owner_id=(select auth.uid()) and status='uploading' and attempts=0 and result is null and worker_id is null and lease_until is null and exists(select 1 from public.portal_tours t where t.id=tour_id and t.owner_id=(select auth.uid())));
revoke all on public.portal_tours, public.portal_revisions, public.portal_captures from anon, authenticated;
grant select, insert on public.portal_tours, public.portal_captures to authenticated;
grant update(title,document,revision,updated_at) on public.portal_tours to authenticated;
grant select on public.portal_revisions to authenticated;
grant all on public.portal_tours, public.portal_revisions, public.portal_captures to service_role;

create or replace function public.portal_publish(p_tour uuid,p_revision integer,p_unpublish boolean default false) returns jsonb
language plpgsql security definer set search_path='' as $$
declare t public.portal_tours; begin
 select * into t from public.portal_tours where id=p_tour and owner_id=auth.uid() for update;
 if not found then raise exception 'Tour not found'; end if;
 if t.revision<>p_revision then raise exception 'Revision conflict'; end if;
 if not p_unpublish then
  if jsonb_array_length(t.document->'scenes')=0 then raise exception 'Add a room before publishing'; end if;
  insert into public.portal_revisions(tour_id,revision,document) values(t.id,t.revision,t.document) on conflict do nothing;
 end if;
 update public.portal_tours set published_revision=case when p_unpublish then null else t.revision end where id=t.id;
 return jsonb_build_object('slug',t.slug,'published_revision',case when p_unpublish then null else t.revision end);
end $$;
revoke all on function public.portal_publish(uuid,integer,boolean) from public,anon;
grant execute on function public.portal_publish(uuid,integer,boolean) to authenticated;

create or replace function public.portal_public_tour(p_slug text) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object('id',t.id,'slug',t.slug,'revision',r.revision,'document',r.document)
 from public.portal_tours t join public.portal_revisions r on r.tour_id=t.id and r.revision=t.published_revision where t.slug=p_slug;
$$;
revoke all on function public.portal_public_tour(text) from public;
grant execute on function public.portal_public_tour(text) to anon,authenticated;
create or replace function public.portal_asset_published(p_path text) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.portal_tours t join public.portal_revisions r on r.tour_id=t.id and r.revision=t.published_revision,
 jsonb_array_elements(r.document->'scenes') s where s->>'assetPath'=p_path);
$$;
revoke all on function public.portal_asset_published(text) from public;
grant execute on function public.portal_asset_published(text) to anon,authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('portal-media','portal-media',false,52428800,array['image/jpeg','image/png','image/webp']) on conflict(id) do nothing;
create policy portal_media_read on storage.objects for select to authenticated using(bucket_id='portal-media' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy portal_media_insert on storage.objects for insert to authenticated with check(bucket_id='portal-media' and (storage.foldername(name))[1]=(select auth.uid())::text and exists(select 1 from public.portal_tours where id::text=(storage.foldername(name))[2] and owner_id=(select auth.uid())));
create policy portal_media_public_read on storage.objects for select to anon,authenticated using(bucket_id='portal-media' and public.portal_asset_published(name));

-- Mutations are narrow RPCs: clients cannot forge successful processing results.
create or replace function public.portal_capture_action(p_id uuid,p_action text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c public.portal_captures; missing integer; begin
 select * into c from public.portal_captures where id=p_id and owner_id=auth.uid() for update;
 if not found then raise exception 'Capture not found'; end if;
 if p_action='cancel' and c.status<>'ready' then
  update public.portal_captures set status='cancelled',worker_id=null,lease_until=null,updated_at=now() where id=p_id;
 elsif p_action in ('complete','retry') and c.status in ('uploading','failed') then
  select count(*) into missing from jsonb_array_elements(c.manifest->'frames') f where not exists(select 1 from storage.objects where bucket_id='portal-media' and name=f->>'path');
  if missing>0 or jsonb_array_length(c.manifest->'frames')<8 then raise exception 'Capture upload is incomplete'; end if;
  update public.portal_captures set status='queued',error=null,attempts=0,updated_at=now() where id=p_id;
 end if;
 return (select to_jsonb(x) from public.portal_captures x where id=p_id);
end $$;
revoke all on function public.portal_capture_action(uuid,text) from public,anon;
grant execute on function public.portal_capture_action(uuid,text) to authenticated;

create or replace function public.portal_claim_capture(p_worker uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c public.portal_captures; begin
 update public.portal_captures set status='failed',error='Processing timed out after three attempts.',lease_until=null where status='processing' and lease_until<now() and attempts>=3;
 select * into c from public.portal_captures where (status='queued' or (status='processing' and lease_until<now())) and attempts<3 order by created_at for update skip locked limit 1;
 if not found then return null; end if;
 update public.portal_captures set status='processing',worker_id=p_worker,lease_until=now()+interval '2 minutes',attempts=attempts+1,updated_at=now() where id=c.id returning * into c;
 return to_jsonb(c);
end $$;
revoke all on function public.portal_claim_capture(uuid) from public,anon,authenticated;
grant execute on function public.portal_claim_capture(uuid) to service_role;

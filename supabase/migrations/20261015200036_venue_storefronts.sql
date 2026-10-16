-- Storefront drafts are independent of Android's v1 tour documents.
create table public.portal_venues (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references auth.users(id) on delete cascade,
 slug text unique not null check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and length(slug) between 3 and 80 and slug not like 'tour-%'),
 document jsonb not null check (jsonb_typeof(document)='object' and octet_length(document::text)<100000),
 main_tour_id uuid references public.portal_tours(id) on delete set null,
 revision integer not null default 1 check (revision>0),
 published_revision integer,
 published_document jsonb,
 published_tour_id uuid references public.portal_tours(id) on delete set null,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index portal_venues_owner on public.portal_venues(owner_id);
create index portal_venues_main_tour on public.portal_venues(main_tour_id);
create index portal_venues_published_tour on public.portal_venues(published_tour_id);
alter table public.portal_venues enable row level security;
create policy venue_owner_read on public.portal_venues for select to authenticated using (owner_id=(select auth.uid()));
revoke all on public.portal_venues from anon, authenticated;
grant select on public.portal_venues to authenticated;
grant all on public.portal_venues to service_role;

-- Only these ownership-checked RPCs can write drafts or public snapshots.
create function public.portal_save_venue(p_id uuid, p_revision integer, p_slug text, p_document jsonb, p_tour uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.portal_venues; begin
 if auth.uid() is null then raise exception 'Please sign in' using errcode='42501'; end if;
 if jsonb_typeof(p_document) is distinct from 'object' or p_document->>'version' is distinct from '1' or length(trim(coalesce(p_document->>'name',''))) not between 1 and 160 or jsonb_typeof(p_document->'buttons') is distinct from 'array' or jsonb_typeof(p_document->'openingHours') is distinct from 'array' then raise exception 'Invalid venue document'; end if;
 if p_tour is not null and not exists(select 1 from public.portal_tours where id=p_tour and owner_id=auth.uid()) then raise exception 'Tour not found' using errcode='42501'; end if;
 if exists(select 1 from public.portal_tours where slug=p_slug) then raise exception 'This link is already used by a tour' using errcode='23505'; end if;
 if p_id is null then
  if p_revision is distinct from 0 then raise exception 'Revision conflict' using errcode='40001'; end if;
  insert into public.portal_venues(owner_id,slug,document,main_tour_id) values(auth.uid(),p_slug,p_document,p_tour) returning * into v;
 else
  select * into v from public.portal_venues where id=p_id and owner_id=auth.uid() for update;
  if not found then raise exception 'Venue not found' using errcode='42501'; end if;
  if v.revision is distinct from p_revision then raise exception 'Revision conflict' using errcode='40001'; end if;
  if v.published_document is not null and v.slug is distinct from p_slug then raise exception 'Published venue links cannot be changed'; end if;
  update public.portal_venues set slug=p_slug,document=p_document,main_tour_id=p_tour,revision=revision+1,updated_at=now() where id=v.id returning * into v;
 end if;
 return to_jsonb(v);
end $$;
revoke all on function public.portal_save_venue(uuid,integer,text,jsonb,uuid) from public,anon;
grant execute on function public.portal_save_venue(uuid,integer,text,jsonb,uuid) to authenticated;

create function public.portal_publish_venue(p_id uuid,p_revision integer,p_unpublish boolean default false)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.portal_venues; a text; begin
 if auth.uid() is null then raise exception 'Please sign in' using errcode='42501'; end if;
 select * into v from public.portal_venues where id=p_id and owner_id=auth.uid() for update;
 if not found then raise exception 'Venue not found' using errcode='42501'; end if;
 if v.revision is distinct from p_revision then raise exception 'Revision conflict' using errcode='40001'; end if;
 if not p_unpublish then
  if v.main_tour_id is not null and not exists(select 1 from public.portal_tours where id=v.main_tour_id and owner_id=auth.uid()) then raise exception 'Tour not found' using errcode='42501'; end if;
  for a in select value from jsonb_each_text(v.document) where key in ('logoAssetPath','coverAssetPath') and value<>'' loop
   if a not like v.owner_id::text||'/'||v.id::text||'/%' or a like '%..%' or not exists(select 1 from storage.objects o where o.bucket_id='portal-venue-media' and o.name=a and (o.metadata->>'size')::bigint between 1 and 10485760 and o.metadata->>'mimetype' in ('image/jpeg','image/png','image/webp')) then raise exception 'Storefront image is missing or not owned by this venue'; end if;
  end loop;
 end if;
 update public.portal_venues set published_revision=case when p_unpublish then null else revision end,
  published_document=case when p_unpublish then published_document else document end,
  published_tour_id=case when p_unpublish then published_tour_id else main_tour_id end
 where id=v.id returning * into v;
 return to_jsonb(v);
end $$;
revoke all on function public.portal_publish_venue(uuid,integer,boolean) from public,anon;
grant execute on function public.portal_publish_venue(uuid,integer,boolean) to authenticated;

create function public.portal_public_venue(p_slug text) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object('id',v.id,'slug',v.slug,'document',v.published_document,'revision',v.published_revision,
  'tourSlug',case when t.published_revision is not null and exists(select 1 from public.portal_revisions r where r.tour_id=t.id and r.revision=t.published_revision) then t.slug else null end)
 from public.portal_venues v left join public.portal_tours t on t.id=v.published_tour_id and t.owner_id=v.owner_id
 where v.slug=p_slug and v.published_revision is not null;
$$;
revoke all on function public.portal_public_venue(text) from public;
grant execute on function public.portal_public_venue(text) to anon,authenticated;

create function public.portal_venue_asset_published(p_path text) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.portal_venues v where v.published_revision is not null
  and p_path like v.owner_id::text||'/'||v.id::text||'/%' and p_path not like '%..%'
  and p_path in (v.published_document->>'logoAssetPath',v.published_document->>'coverAssetPath'));
$$;
revoke all on function public.portal_venue_asset_published(text) from public;
grant execute on function public.portal_venue_asset_published(text) to anon,authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('portal-venue-media','portal-venue-media',false,10485760,array['image/jpeg','image/png','image/webp']);
create policy venue_media_owner_read on storage.objects for select to authenticated using(bucket_id='portal-venue-media' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy venue_media_owner_insert on storage.objects for insert to authenticated with check(bucket_id='portal-venue-media' and (storage.foldername(name))[1]=(select auth.uid())::text and exists(select 1 from public.portal_venues v where v.id::text=(storage.foldername(name))[2] and v.owner_id=(select auth.uid())));
create policy venue_media_published_read on storage.objects for select to anon,authenticated using(bucket_id='portal-venue-media' and public.portal_venue_asset_published(name));

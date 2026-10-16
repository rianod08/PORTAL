create schema if not exists portal_private;
revoke all on schema portal_private from public,anon,authenticated;
create table if not exists portal_private.workers(user_id uuid primary key references auth.users(id) on delete cascade);
create or replace function public.portal_is_worker() returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from portal_private.workers where user_id=auth.uid());
$$;
revoke all on function public.portal_is_worker() from public,anon;
grant execute on function public.portal_is_worker() to authenticated;
alter table public.portal_captures add column if not exists worker_auth_id uuid;
grant update(status,result,error,lease_until,updated_at) on public.portal_captures to authenticated;
create policy worker_capture_read on public.portal_captures for select to authenticated using(public.portal_is_worker() and worker_auth_id=(select auth.uid()));
create policy worker_capture_update on public.portal_captures for update to authenticated using(public.portal_is_worker() and worker_auth_id=(select auth.uid()) and status='processing' and lease_until>now()) with check(public.portal_is_worker() and worker_auth_id=(select auth.uid()));
create policy worker_source_media on storage.objects for select to authenticated using(bucket_id='portal-media' and public.portal_is_worker() and exists(select 1 from public.portal_captures c where c.worker_auth_id=(select auth.uid()) and c.status='processing' and c.lease_until>now() and storage.objects.name like c.owner_id::text||'/'||c.tour_id::text||'/captures/'||c.id::text||'/%'));
create policy worker_result_media on storage.objects for insert to authenticated with check(bucket_id='portal-media' and public.portal_is_worker() and exists(select 1 from public.portal_captures c where c.worker_auth_id=(select auth.uid()) and c.status='processing' and c.lease_until>now() and storage.objects.name in(c.owner_id::text||'/'||c.tour_id::text||'/processed/'||c.id::text||'/'||c.worker_id::text||'/panorama.jpg',c.owner_id::text||'/'||c.tour_id::text||'/processed/'||c.id::text||'/'||c.worker_id::text||'/preview.jpg')));
create or replace function public.portal_claim_capture(p_worker uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c public.portal_captures; begin
 if coalesce(auth.role(),'') <> 'service_role' and not public.portal_is_worker() then raise exception 'Worker access required'; end if;
 update public.portal_captures set status='failed',error='Processing timed out after three attempts.',lease_until=null where status='processing' and lease_until<now() and attempts>=3;
 select * into c from public.portal_captures where (status='queued' or (status='processing' and lease_until<now())) and attempts<3 order by created_at for update skip locked limit 1;
 if not found then return null; end if;
 update public.portal_captures set status='processing',worker_id=p_worker,worker_auth_id=auth.uid(),lease_until=now()+interval '2 minutes',attempts=attempts+1,updated_at=now() where id=c.id returning * into c;
 return to_jsonb(c);
end $$;
grant execute on function public.portal_claim_capture(uuid) to authenticated;

-- Public media must belong to the published tour, never another owner's raw capture.
create or replace function public.portal_asset_published(p_path text) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.portal_tours t join public.portal_revisions r on r.tour_id=t.id and r.revision=t.published_revision,
 jsonb_array_elements(r.document->'scenes') s where s->>'assetPath'=p_path and p_path like t.owner_id::text||'/'||t.id::text||'/%' and p_path not like '%/captures/%');
$$;
create or replace function public.portal_publish(p_tour uuid,p_revision integer,p_unpublish boolean default false) returns jsonb
language plpgsql security definer set search_path='' as $$
declare t public.portal_tours; begin
 select * into t from public.portal_tours where id=p_tour and owner_id=auth.uid() for update;
 if not found then raise exception 'Tour not found'; end if;
 if t.revision<>p_revision then raise exception 'Revision conflict'; end if;
 if not p_unpublish then
  if jsonb_typeof(t.document->'scenes')<>'array' or jsonb_array_length(t.document->'scenes')=0 then raise exception 'Add a room before publishing'; end if;
  if exists(select 1 from jsonb_array_elements(t.document->'scenes') s where coalesce(s->>'assetPath','') not like t.owner_id::text||'/'||t.id::text||'/%' or s->>'assetPath' like '%/captures/%' or not exists(select 1 from storage.objects o where o.bucket_id='portal-media' and o.name=s->>'assetPath')) then raise exception 'Tour media is missing or not owned by this tour'; end if;
  insert into public.portal_revisions(tour_id,revision,document) values(t.id,t.revision,t.document) on conflict do nothing;
 end if;
 update public.portal_tours set published_revision=case when p_unpublish then null else t.revision end where id=t.id;
 return jsonb_build_object('slug',t.slug,'published_revision',case when p_unpublish then null else t.revision end);
end $$;

-- Rich tour assets retain the existing account/tour ownership boundary.
create or replace function portal_private.document_assets(p_document jsonb, p_enabled_only boolean default false)
returns setof text language sql immutable set search_path='' as $$
 select p_document->'branding'->>'logoAssetPath' where coalesce(p_document->'branding'->>'logoAssetPath','')<>''
 union select s->>'assetPath' from jsonb_array_elements(p_document->'scenes') s where coalesce(s->>'assetPath','')<>''
 union select p->>'assetPath' from jsonb_array_elements(p_document->'scenes') s,
 jsonb_array_elements(s->'points') p where coalesce(p->>'assetPath','')<>'' and (not p_enabled_only or p->>'enabled'='true');
$$;
revoke all on function portal_private.document_assets(jsonb,boolean) from public,anon,authenticated;

create or replace function public.portal_asset_published(p_path text) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.portal_tours t join public.portal_revisions r on r.tour_id=t.id and r.revision=t.published_revision,
 portal_private.document_assets(r.document,true) a where a=p_path and p_path like t.owner_id::text||'/'||t.id::text||'/%' and p_path not like '%/captures/%' and p_path not like '%..%');
$$;

create or replace function public.portal_publish(p_tour uuid,p_revision integer,p_unpublish boolean default false) returns jsonb
language plpgsql security definer set search_path='' as $$
declare t public.portal_tours; begin
 select * into t from public.portal_tours where id=p_tour and owner_id=auth.uid() for update;
 if not found then raise exception 'Tour not found'; end if;
 if t.revision<>p_revision then raise exception 'Revision conflict'; end if;
 if not p_unpublish then
  if jsonb_typeof(t.document->'scenes')<>'array' or jsonb_array_length(t.document->'scenes')=0 then raise exception 'Add a room before publishing'; end if;
  if exists(select 1 from jsonb_array_elements(t.document->'scenes') s where coalesce(s->>'assetPath','')='') then raise exception 'A room is missing its panorama'; end if;
  if exists(select 1 from portal_private.document_assets(t.document) a where a not like t.owner_id::text||'/'||t.id::text||'/%' or a like '%/captures/%' or a like '%..%' or not exists(select 1 from storage.objects o where o.bucket_id='portal-media' and o.name=a and (o.metadata->>'size')::bigint<=52428800 and o.metadata->>'mimetype' in ('image/jpeg','image/png','image/webp','video/mp4'))) then raise exception 'Tour media is missing, unsupported, or not owned by this tour'; end if;
  insert into public.portal_revisions(tour_id,revision,document) values(t.id,t.revision,t.document) on conflict do nothing;
 end if;
 update public.portal_tours set published_revision=case when p_unpublish then null else t.revision end where id=t.id;
 return jsonb_build_object('slug',t.slug,'published_revision',case when p_unpublish then null else t.revision end);
end $$;
update storage.buckets set allowed_mime_types=array['image/jpeg','image/png','image/webp','video/mp4'],file_size_limit=52428800 where id='portal-media';

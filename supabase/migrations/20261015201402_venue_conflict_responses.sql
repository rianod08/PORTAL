create or replace function public.portal_save_venue(p_id uuid, p_revision integer, p_slug text, p_document jsonb, p_tour uuid default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.portal_venues; begin
 if auth.uid() is null then raise exception 'Please sign in' using errcode='42501'; end if;
 if jsonb_typeof(p_document) is distinct from 'object' or p_document->>'version' is distinct from '1' or length(trim(coalesce(p_document->>'name',''))) not between 1 and 160 or jsonb_typeof(p_document->'buttons') is distinct from 'array' or jsonb_typeof(p_document->'openingHours') is distinct from 'array' then raise exception 'Invalid venue document'; end if;
 if p_tour is not null and not exists(select 1 from public.portal_tours where id=p_tour and owner_id=auth.uid()) then raise exception 'Tour not found' using errcode='42501'; end if;
 if exists(select 1 from public.portal_tours where slug=p_slug) then raise exception 'This link is already used by a tour' using errcode='23505'; end if;
 if p_id is null then
  if p_revision is distinct from 0 then raise exception 'Revision conflict' using errcode='PT409'; end if;
  insert into public.portal_venues(owner_id,slug,document,main_tour_id) values(auth.uid(),p_slug,p_document,p_tour) returning * into v;
 else
  select * into v from public.portal_venues where id=p_id and owner_id=auth.uid() for update;
  if not found then raise exception 'Venue not found' using errcode='42501'; end if;
  if v.revision is distinct from p_revision then raise exception 'Revision conflict' using errcode='PT409'; end if;
  if v.published_document is not null and v.slug is distinct from p_slug then raise exception 'Published venue links cannot be changed'; end if;
  update public.portal_venues set slug=p_slug,document=p_document,main_tour_id=p_tour,revision=revision+1,updated_at=now() where id=v.id returning * into v;
 end if;
 return to_jsonb(v);
end $$;

create or replace function public.portal_publish_venue(p_id uuid,p_revision integer,p_unpublish boolean default false)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.portal_venues; a text; begin
 if auth.uid() is null then raise exception 'Please sign in' using errcode='42501'; end if;
 select * into v from public.portal_venues where id=p_id and owner_id=auth.uid() for update;
 if not found then raise exception 'Venue not found' using errcode='42501'; end if;
 if v.revision is distinct from p_revision then raise exception 'Revision conflict' using errcode='PT409'; end if;
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

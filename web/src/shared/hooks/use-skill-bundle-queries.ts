import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { PagedResponse, SearchParams, SkillBundleDetail, SkillBundleDraftRequest, SkillBundleSummary } from '@/api/types'
import { skillBundleApi } from '@/api/client'
import { getSkillBundleDetailQueryKey } from './query-keys'

async function searchSkillBundles(params: SearchParams): Promise<PagedResponse<SkillBundleSummary>> {
  return skillBundleApi.search(params)
}

async function getSkillBundleDetail(namespace: string, slug: string): Promise<SkillBundleDetail> {
  return skillBundleApi.getDetail(namespace, slug)
}

export function useSearchSkillBundles(params: SearchParams) {
  return useQuery({
    queryKey: ['skillBundles', 'search', params],
    queryFn: () => searchSkillBundles(params),
  })
}

export function useSkillBundleDetail(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: getSkillBundleDetailQueryKey(namespace, slug),
    queryFn: () => getSkillBundleDetail(namespace, slug),
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useCreateSkillBundle() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: SkillBundleDraftRequest) => skillBundleApi.create(request),
    onSuccess: (bundle) => {
      queryClient.invalidateQueries({ queryKey: ['skillBundles'] })
      queryClient.setQueryData(getSkillBundleDetailQueryKey(bundle.namespace, bundle.slug), bundle)
    },
  })
}

export function useUpdateSkillBundle(namespace: string, slug: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: SkillBundleDraftRequest) => skillBundleApi.update(namespace, slug, request),
    onSuccess: (bundle) => {
      queryClient.invalidateQueries({ queryKey: ['skillBundles'] })
      queryClient.setQueryData(getSkillBundleDetailQueryKey(bundle.namespace, bundle.slug), bundle)
    },
  })
}

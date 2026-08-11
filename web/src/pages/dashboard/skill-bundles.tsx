import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Plus, Trash2 } from 'lucide-react'
import type { SkillBundleDetail, SkillBundleDraftRequest, SkillBundleItem, SkillSummary } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue, normalizeSelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useCreateSkillBundle, useSkillBundleDetail, useUpdateSkillBundle } from '@/shared/hooks/use-skill-bundle-queries'
import { toast } from '@/shared/lib/toast'

const EMPTY_NAMESPACE_VALUE = '__select_namespace__'

type SelectedSkill = {
  skillId: number
  namespace: string
  skillSlug: string
  displayName: string
  note: string
}

type FormState = {
  namespace: string
  name: string
  slug: string
  summary: string
  avatarUrl: string
  description: string
  roleDescription: string
  applicableScenarios: string
  methodology: string
  recommendedSkillNotes: string
  visibility: 'PUBLIC' | 'NAMESPACE_ONLY' | 'PRIVATE'
  status: 'DRAFT' | 'PUBLISHED'
  labels: string[]
}

const emptyForm: FormState = {
  namespace: '',
  name: '',
  slug: '',
  summary: '',
  avatarUrl: '',
  description: '',
  roleDescription: '',
  applicableScenarios: '',
  methodology: '',
  recommendedSkillNotes: '',
  visibility: 'PRIVATE',
  status: 'DRAFT',
  labels: [],
}

function toSelectedSkill(item: SkillBundleItem): SelectedSkill {
  return {
    skillId: item.skillId,
    namespace: item.namespace,
    skillSlug: item.skillSlug,
    displayName: item.displayName,
    note: item.note ?? '',
  }
}

function toForm(bundle: SkillBundleDetail): FormState {
  return {
    namespace: bundle.namespace,
    name: bundle.name,
    slug: bundle.slug,
    summary: bundle.summary ?? '',
    avatarUrl: bundle.avatarUrl ?? '',
    description: bundle.description ?? '',
    roleDescription: bundle.roleDescription ?? '',
    applicableScenarios: bundle.applicableScenarios ?? '',
    methodology: bundle.methodology ?? '',
    recommendedSkillNotes: bundle.recommendedSkillNotes ?? '',
    visibility: bundle.visibility === 'PUBLIC' || bundle.visibility === 'NAMESPACE_ONLY' ? bundle.visibility : 'PRIVATE',
    status: bundle.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
    labels: (bundle.labels ?? []).map((label) => label.slug),
  }
}

function normalizeSlug(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
}

function buildRequest(form: FormState, selectedSkills: SelectedSkill[]): SkillBundleDraftRequest {
  return {
    namespace: form.namespace,
    name: form.name.trim(),
    slug: normalizeSlug(form.slug),
    summary: form.summary.trim() || undefined,
    avatarUrl: form.avatarUrl.trim() || undefined,
    description: form.description.trim() || undefined,
    roleDescription: form.roleDescription.trim() || undefined,
    applicableScenarios: form.applicableScenarios.trim() || undefined,
    methodology: form.methodology.trim() || undefined,
    recommendedSkillNotes: form.recommendedSkillNotes.trim() || undefined,
    visibility: form.visibility,
    status: form.status,
    labels: form.labels,
    items: selectedSkills.map((skill) => ({
      skillId: skill.skillId,
      note: skill.note.trim() || undefined,
    })),
  }
}

function SkillBundleEditor({ editTarget }: { editTarget?: { namespace: string; slug: string } }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isEditing = !!editTarget
  const [form, setForm] = useState<FormState>(emptyForm)
  const [selectedSkills, setSelectedSkills] = useState<SelectedSkill[]>([])
  const [skillSearch, setSkillSearch] = useState('')

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const { data: labels } = useVisibleLabels()
  const { data: detail, isLoading: isLoadingDetail } = useSkillBundleDetail(editTarget?.namespace ?? '', editTarget?.slug ?? '', isEditing)
  const { data: skillResults } = useSearchSkills({
    q: skillSearch,
    namespace: form.namespace || undefined,
    size: 6,
    page: 0,
    sort: 'relevance',
  })
  const createMutation = useCreateSkillBundle()
  const updateMutation = useUpdateSkillBundle(editTarget?.namespace ?? '', editTarget?.slug ?? '')
  const visibleLabels = (labels ?? []).filter((label) => label.type === 'RECOMMENDED')
  const selectedSkillIds = useMemo(() => new Set(selectedSkills.map((skill) => skill.skillId)), [selectedSkills])
  const isSubmitting = createMutation.isPending || updateMutation.isPending

  useEffect(() => {
    if (detail) {
      setForm(toForm(detail))
      setSelectedSkills(detail.items.map(toSelectedSkill))
    }
  }, [detail])

  const updateForm = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }

  const handleAddSkill = (skill: SkillSummary) => {
    if (selectedSkillIds.has(skill.id)) {
      return
    }
    setSelectedSkills((current) => [
      ...current,
      {
        skillId: skill.id,
        namespace: skill.namespace,
        skillSlug: skill.slug,
        displayName: skill.displayName,
        note: '',
      },
    ])
  }

  const handleSubmit = async () => {
    const normalizedSlug = normalizeSlug(form.slug)
    if (!form.namespace || !form.name.trim() || !normalizedSlug) {
      toast.error(t('skillBundleEditor.validationTitle'), t('skillBundleEditor.requiredFields'))
      return
    }
    if (form.status === 'PUBLISHED' && selectedSkills.length === 0) {
      toast.error(t('skillBundleEditor.validationTitle'), t('skillBundleEditor.publishedNeedsSkill'))
      return
    }

    const request = buildRequest({ ...form, slug: normalizedSlug }, selectedSkills)
    try {
      const bundle = isEditing
        ? await updateMutation.mutateAsync(request)
        : await createMutation.mutateAsync(request)
      toast.success(isEditing ? t('skillBundleEditor.updateSuccessTitle') : t('skillBundleEditor.createSuccessTitle'))
      navigate({ to: `/skill-bundles/${bundle.namespace}/${encodeURIComponent(bundle.slug)}` })
    } catch (error) {
      toast.error(t('skillBundleEditor.saveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  if (isEditing && isLoadingDetail) {
    return (
      <div className="space-y-4 animate-fade-up">
        <div className="h-9 w-56 animate-shimmer rounded-lg" />
        <div className="h-96 animate-shimmer rounded-xl" />
      </div>
    )
  }

  return (
    <div className="max-w-5xl space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={isEditing ? t('skillBundleEditor.editTitle') : t('skillBundleEditor.createTitle')}
        subtitle={t('skillBundleEditor.subtitle')}
        actions={(
          <Button type="button" onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? t('skillBundleEditor.saving') : t('skillBundleEditor.save')}
          </Button>
        )}
      />

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-5">
          <Card className="space-y-5 p-6">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="bundle-namespace">{t('skillBundleEditor.namespace')}</Label>
                {isLoadingNamespaces ? (
                  <div className="h-11 animate-shimmer rounded-lg" />
                ) : (
                  <Select
                    value={normalizeSelectValue(form.namespace) ?? EMPTY_NAMESPACE_VALUE}
                    onValueChange={(value) => {
                      const namespace = value === EMPTY_NAMESPACE_VALUE ? '' : value
                      setSelectedSkills([])
                      updateForm('namespace', namespace)
                    }}
                    disabled={isEditing}
                  >
                    <SelectTrigger id="bundle-namespace">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={EMPTY_NAMESPACE_VALUE}>{t('skillBundleEditor.selectNamespace')}</SelectItem>
                      {(namespaces ?? []).map((namespace) => (
                        <SelectItem key={namespace.id} value={namespace.slug}>
                          {namespace.displayName} (@{namespace.slug})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="bundle-visibility">{t('skillBundleEditor.visibility')}</Label>
                <Select value={form.visibility} onValueChange={(value) => updateForm('visibility', value as FormState['visibility'])}>
                  <SelectTrigger id="bundle-visibility">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PRIVATE">{t('skillBundles.visibility.PRIVATE')}</SelectItem>
                    <SelectItem value="NAMESPACE_ONLY">{t('skillBundles.visibility.NAMESPACE_ONLY')}</SelectItem>
                    <SelectItem value="PUBLIC">{t('skillBundles.visibility.PUBLIC')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="bundle-name">{t('skillBundleEditor.name')}</Label>
                <Input id="bundle-name" value={form.name} onChange={(event) => updateForm('name', event.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bundle-slug">{t('skillBundleEditor.slug')}</Label>
                <Input id="bundle-slug" value={form.slug} onChange={(event) => updateForm('slug', normalizeSlug(event.target.value))} />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="bundle-summary">{t('skillBundleEditor.summary')}</Label>
              <Textarea id="bundle-summary" value={form.summary} onChange={(event) => updateForm('summary', event.target.value)} />
            </div>

            <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_6rem] md:items-end">
              <div className="space-y-2">
                <Label htmlFor="bundle-avatar-url">{t('skillBundleEditor.avatarUrl')}</Label>
                <Input
                  id="bundle-avatar-url"
                  value={form.avatarUrl}
                  onChange={(event) => updateForm('avatarUrl', event.target.value)}
                  placeholder={t('skillBundleEditor.avatarUrlPlaceholder')}
                />
              </div>
              <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-xl border border-border bg-secondary/60">
                {form.avatarUrl.trim() ? (
                  <img
                    src={form.avatarUrl.trim()}
                    alt={t('skillBundleEditor.avatarPreviewAlt')}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="text-xs text-muted-foreground">{t('skillBundleEditor.avatarEmpty')}</span>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="bundle-description">{t('skillBundleEditor.description')}</Label>
              <Textarea id="bundle-description" value={form.description} onChange={(event) => updateForm('description', event.target.value)} />
            </div>
          </Card>

          <Card className="space-y-5 p-6">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="bundle-role">{t('skillBundleEditor.roleDescription')}</Label>
                <Textarea id="bundle-role" value={form.roleDescription} onChange={(event) => updateForm('roleDescription', event.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bundle-scenarios">{t('skillBundleEditor.applicableScenarios')}</Label>
                <Textarea id="bundle-scenarios" value={form.applicableScenarios} onChange={(event) => updateForm('applicableScenarios', event.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bundle-methodology">{t('skillBundleEditor.methodology')}</Label>
                <Textarea id="bundle-methodology" value={form.methodology} onChange={(event) => updateForm('methodology', event.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bundle-notes">{t('skillBundleEditor.recommendedSkillNotes')}</Label>
                <Textarea id="bundle-notes" value={form.recommendedSkillNotes} onChange={(event) => updateForm('recommendedSkillNotes', event.target.value)} />
              </div>
            </div>
          </Card>
        </div>

        <aside className="space-y-5">
          <Card className="space-y-4 p-5">
            <div className="space-y-2">
              <Label htmlFor="bundle-status">{t('skillBundleEditor.status')}</Label>
              <Select value={form.status} onValueChange={(value) => updateForm('status', value as FormState['status'])}>
                <SelectTrigger id="bundle-status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DRAFT">{t('skillBundles.status.DRAFT')}</SelectItem>
                  <SelectItem value="PUBLISHED">{t('skillBundles.status.PUBLISHED')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('skillBundleEditor.labels')}</Label>
              <div className="flex flex-wrap gap-2">
                {visibleLabels.map((label) => {
                  const selected = form.labels.includes(label.slug)
                  return (
                    <Button
                      key={label.slug}
                      type="button"
                      size="sm"
                      variant={selected ? 'default' : 'outline'}
                      onClick={() => updateForm(
                        'labels',
                        selected ? form.labels.filter((slug) => slug !== label.slug) : [...form.labels, label.slug],
                      )}
                    >
                      {label.displayName}
                    </Button>
                  )
                })}
              </div>
            </div>
          </Card>

          <Card className="space-y-4 p-5">
            <div className="space-y-2">
              <Label htmlFor="bundle-skill-search">{t('skillBundleEditor.skillSearch')}</Label>
              <Input
                id="bundle-skill-search"
                type="search"
                value={skillSearch}
                onChange={(event) => setSkillSearch(event.target.value)}
                placeholder={t('skillBundleEditor.skillSearchPlaceholder')}
                disabled={!form.namespace}
              />
            </div>
            <div className="space-y-2">
              {(skillResults?.items ?? []).filter((skill) => !selectedSkillIds.has(skill.id)).map((skill) => (
                <div key={skill.id} className="flex items-start justify-between gap-3 rounded-lg border border-border/60 p-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-foreground">{skill.displayName}</div>
                    <div className="truncate text-xs text-muted-foreground">@{skill.namespace}/{skill.slug}</div>
                  </div>
                  <Button type="button" size="sm" variant="outline" onClick={() => handleAddSkill(skill)}>
                    <Plus className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>
          </Card>

          <Card className="space-y-4 p-5">
            <div>
              <h2 className="text-sm font-semibold text-foreground">{t('skillBundleEditor.selectedSkills')}</h2>
              <p className="mt-1 text-xs text-muted-foreground">{t('skillBundleEditor.selectedSkillsHint')}</p>
            </div>
            {selectedSkills.length > 0 ? (
              <div className="space-y-3">
                {selectedSkills.map((skill, index) => (
                  <div key={skill.skillId} className="space-y-2 rounded-lg border border-border/60 p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-semibold text-foreground">{index + 1}. {skill.displayName}</div>
                        <div className="truncate text-xs text-muted-foreground">@{skill.namespace}/{skill.skillSlug}</div>
                      </div>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => setSelectedSkills((current) => current.filter((item) => item.skillId !== skill.skillId))}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                    <Textarea
                      value={skill.note}
                      onChange={(event) => {
                        const note = event.target.value
                        setSelectedSkills((current) => current.map((item) => (
                          item.skillId === skill.skillId ? { ...item, note } : item
                        )))
                      }}
                      placeholder={t('skillBundleEditor.skillNotePlaceholder')}
                      className="min-h-[72px]"
                    />
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t('skillBundleEditor.noSelectedSkills')}</p>
            )}
          </Card>
        </aside>
      </div>
    </div>
  )
}

export function CreateSkillBundlePage() {
  return <SkillBundleEditor />
}

export function EditSkillBundlePage() {
  const { namespace, slug } = useParams({ from: '/dashboard/skill-bundles/$namespace/$slug' })
  return <SkillBundleEditor editTarget={{ namespace, slug }} />
}

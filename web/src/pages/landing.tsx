import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { PackageOpen, Terminal, Shield, Users, GitBranch, Search as SearchIcon, Settings } from 'lucide-react'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useInView } from '@/shared/hooks/use-in-view'
import { Button } from '@/shared/ui/button'
import { SvgIcon } from "@/shared/components/svg-icon";

/**
 * Marketing-style landing page for unauthenticated and first-time visitors.
 *
 * The page mixes static positioning content with live skill queries so popular and latest skills
 * stay aligned with the current registry state.
 */
export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    sort: 'downloads',
    size: 6,
  })

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    sort: 'newest',
    size: 6,
  })

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const heroView = useInView()
  const featuresView = useInView()
  const quickStartView = useInView()
  const popularView = useInView()
  const latestView = useInView()

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  const features = [
    {
      icon: <Shield className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.secure.title'),
      description: t('landing.features.secure.description'),
    },
    {
      icon: <Users className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.community.title'),
      description: t('landing.features.community.description'),
    },
    {
      icon: <PackageOpen className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.integration.title'),
      description: t('landing.features.integration.description'),
    },
    {
      icon: <GitBranch className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.versionControl.title', { defaultValue: 'Version control' }),
      description: t('landing.features.versionControl.description', { defaultValue: 'Managed release flows keep skill packages traceable and easier to review.' }),
    },
    {
      icon: <Terminal className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.cli.title', { defaultValue: 'CLI tooling' }),
      description: t('landing.features.cli.description', { defaultValue: 'Command-line workflows support publishing, installing, and operating skills quickly.' }),
    },
    {
      icon: <Settings className="w-6 h-6" strokeWidth={2} style={{ color: '#bf3732' }} />,
      title: t('landing.features.governance.title', { defaultValue: 'Governance' }),
      description: t('landing.features.governance.description', { defaultValue: 'Built-in review and permission flows help teams enforce skill quality.' }),
    },
  ]

  return (
    <>
      {/* Hero Section */}
      <main
        ref={heroView.ref}
        className={`relative z-10 flex flex-col items-center pt-16 pb-20 px-4 md:pt-24 scroll-fade-up${heroView.inView ? " in-view" : ""}`}
      >
        {/* Decorative background blobs */}
        {/* <div className="absolute inset-0 overflow-hidden pointer-events-none hidden md:block">
          <div className="absolute top-[-160px] left-1/2 -translate-x-1/2 w-[900px] h-[900px] rounded-full bg-brand-gradient opacity-[0.06] blur-3xl" />
          <div className="absolute top-[100px] right-[calc(50%-500px)] w-[500px] h-[600px] rounded-full bg-brand-gradient opacity-[0.04] blur-3xl rotate-[35deg]" />
          <div className="absolute top-[200px] left-[calc(50%-450px)] w-[400px] h-[500px] rounded-full bg-brand-gradient opacity-[0.05] blur-3xl rotate-[75deg]" />
        </div> */}

        {/* Brand logo mark */}
        <div className="w-[352px] h-[44px] rounded-[12px] flex items-center justify-center mb-5">
          {/* <span className="text-white text-[22px] font-bold leading-none" style={{ fontFamily: 'Arial Rounded MT Bold, Syne, sans-serif' }}>R</span> */}
          <SvgIcon name="svg-text-HeroSkillhub" className="h-[44px] w-[352px]" />
        </div>
        <h1
          className="text-xl md:text-2xl font-semibold tracking-tight text-center mb-3"
          style={{ color: "hsl(var(--foreground))" }}
        >
          {t("landing.hero.title")}
        </h1>
        <p
          className="text-base md:text-lg text-center max-w-2xl mb-10 leading-relaxed"
          style={{ color: "hsl(var(--text-secondary))" }}
        >
          {t("landing.hero.subtitle")}
        </p>

        {/* Search box */}
        <div className="w-full max-w-[700px] mb-8">
          <div
            className="flex items-center bg-white rounded-[12px] border h-[56px] px-5 shadow-[0_2px_15px_1px_rgba(159,69,66,0.1)]"
            style={{ borderColor: "#e5e5e5" }}
          >
            <SearchIcon
              className="w-6 h-6 flex-shrink-0 mr-3"
              style={{ color: "#999" }}
              strokeWidth={1.5}
            />
            <input
              type="text"
              placeholder={t("landing.hero.searchPlaceholder")}
              className="hero-input flex-1 bg-transparent outline-none text-[15px]"
              style={{ color: "hsl(var(--foreground))" }}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  handleSearch((e.target as HTMLInputElement).value);
                }
              }}
            />
          </div>
        </div>

        {/* CTA buttons */}
        <div className="flex flex-wrap justify-center gap-6 mb-16">
          <Link
            to="/search"
            search={{ q: "", sort: "relevance", page: 0, starredOnly: false }}
            className="flex items-center justify-center h-[52px] px-[32px] rounded-[12px] text-[16px] font-semibold text-white bg-brand-gradient hover:opacity-95 transition-opacity"
            style={{ boxShadow: "0 2px 4px rgba(159,69,66,0.12)" }}
          >
            {t("landing.hero.exploreSkills")}
          </Link>
          <Link
            to="/dashboard/publish"
            className="flex items-center justify-center h-[52px] px-[32px] rounded-[12px] text-[16px] font-medium bg-white border transition-colors hover:border-[#bf3732] hover:text-[#bf3732]"
            style={{ borderColor: "rgba(204,204,204,0.8)", color: "#333" }}
          >
            {t("landing.hero.publishSkill", { defaultValue: "开始构建" })}
          </Link>
        </div>

        {/* Stats row */}
        <div className="flex items-start justify-center gap-16 md:gap-24">
          {[
            { value: "1000+", label: t("landing.stats.skills") },
            { value: "50K+", label: t("landing.stats.downloads") },
            { value: "200+", label: t("landing.stats.teams") },
          ].map((stat) => (
            <div key={stat.label} className="flex flex-col items-center">
              <span
                className="text-[38px] font-bold leading-none text-brand-gradient"
                style={{ textShadow: "0 2px 8px rgba(159,69,66,0.12)" }}
              >
                {stat.value}
              </span>
              <span className="text-[14px] mt-[12px]" style={{ color: "#333" }}>
                {stat.label}
              </span>
            </div>
          ))}
        </div>
      </main>

      {/* Features Section */}
      <section
        ref={featuresView.ref}
        className={`relative z-10 w-full pt-[96px] pb-20 md:pb-24 px-6 scroll-fade-up${featuresView.inView ? " in-view" : ""}`}
        style={{ background: "var(--bg-page, hsl(var(--background)))" }}
      >
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-14">
            <h2
              className="text-3xl md:text-4xl font-bold tracking-tight mb-3"
              style={{ color: "#333" }}
            >
              {t("landing.whyTitle", { defaultValue: "为什么选择" })}{" "}
              <span
                className="text-brand-gradient"
                style={{ textShadow: "0 2px 8px rgba(159,69,66,0.12)" }}
              >
                HeroSkillHub
              </span>
            </h2>
            <p
              className="text-base md:text-lg max-w-2xl mx-auto leading-relaxed"
              style={{ color: "#666" }}
            >
              {t("landing.whySkillHub.subtitle", {
                defaultValue: "专为企业打造的私有化 Agent 技能管理平台",
              })}
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {features.map((feature, idx) => (
              <div
                key={feature.title}
                className="bg-white rounded-[20px] p-8 border transition-shadow hover:shadow-md"
                style={{
                  borderColor: "#e5e5e5",
                  boxShadow:
                    idx === 1
                      ? "1px 2px 16px 1px rgba(191,55,50,0.1)"
                      : undefined,
                }}
              >
                <div
                  className="w-12 h-12 rounded-[12px] flex items-center justify-center mb-6 mx-auto bg-brand-gradient/10"
                  style={{ boxShadow: "0 2px 8px rgba(159,69,66,0.12)" }}
                >
                  <div className="text-brand-gradient">{feature.icon}</div>
                </div>
                <h3
                  className="text-lg font-semibold text-center mb-3"
                  style={{ color: "#333" }}
                >
                  {feature.title}
                </h3>
                <p
                  className="text-sm text-center leading-relaxed"
                  style={{ color: "#666" }}
                >
                  {feature.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Quick Start */}
      <div
        ref={quickStartView.ref}
        className={`scroll-fade-up${quickStartView.inView ? " in-view" : ""}`}
      >
        <LandingQuickStartSection />
      </div>

      {/* Popular Downloads Section */}
      <section
        ref={popularView.ref}
        className={`relative z-10 w-full py-20 md:py-24 px-6 scroll-fade-up${popularView.inView ? " in-view" : ""}`}
        style={{ background: "var(--bg-page, hsl(var(--background)))" }}
      >
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2
                className="text-[30px] font-bold tracking-tight mb-2"
                style={{ color: "#333" }}
              >
                {t("home.popularTitle")}
              </h2>
              <p className="text-[18px]" style={{ color: "#666" }}>
                {t("home.popularDescription")}
              </p>
            </div>
            <Button
              variant="ghost"
              className="text-[14px] font-medium"
              style={{ color: "#333" }}
              onClick={() =>
                navigate({
                  to: "/search",
                  search: {
                    q: "",
                    sort: "downloads",
                    page: 0,
                    starredOnly: false,
                  },
                })
              }
            >
              {t("home.viewAll")} →
            </Button>
          </div>
          {isLoadingPopular ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {popularSkills?.items.map((skill, idx) => (
                <div
                  key={skill.id}
                  className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}
                >
                  <SkillCard
                    skill={skill}
                    onClick={() =>
                      handleSkillClick(skill.namespace, skill.slug)
                    }
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Latest Releases Section */}
      <section
        ref={latestView.ref}
        className={`relative z-10 w-full py-20 md:py-24 px-6 scroll-fade-up${latestView.inView ? " in-view" : ""}`}
        style={{ background: "var(--bg-page, hsl(var(--background)))" }}
      >
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2
                className="text-[30px] font-bold tracking-tight mb-2"
                style={{ color: "#333" }}
              >
                {t("home.latestTitle")}
              </h2>
              <p className="text-[18px]" style={{ color: "#666" }}>
                {t("home.latestDescription")}
              </p>
            </div>
            <Button
              variant="ghost"
              className="text-[14px] font-medium"
              style={{ color: "#bf3732" }}
              onClick={() =>
                navigate({
                  to: "/search",
                  search: {
                    q: "",
                    sort: "newest",
                    page: 0,
                    starredOnly: false,
                  },
                })
              }
            >
              {t("home.viewAll")} →
            </Button>
          </div>
          {isLoadingLatest ? (
            <SkeletonList count={6} />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {latestSkills?.items.map((skill, idx) => (
                <div
                  key={skill.id}
                  className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}
                >
                  <SkillCard
                    skill={skill}
                    onClick={() =>
                      handleSkillClick(skill.namespace, skill.slug)
                    }
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  );
}

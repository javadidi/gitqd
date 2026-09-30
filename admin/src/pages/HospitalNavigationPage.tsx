import type { ReactNode } from 'react'
import PageHeader from '@/components/business/PageHeader'

function Quote({ children }: { children: ReactNode }) {
  return (
    <p className="nav-spec-quote rounded-md border-l-2 border-muted-foreground/30 bg-muted/30 py-2 pl-3 text-sm">
      {children}
    </p>
  )
}

/**
 * 医院导航管理（PRD 4.5.8）——**这一页是说明页，不是功能页**。
 *
 * <h2>先把"卡片确实要了"这件事说清楚</h2>
 * 任务卡 743 行（T27「要做什么」）白纸黑字写着「医院导航管理：CRUD」。
 * 所以这不是"规格里没提"，是"规格提了，但按另外三条证据首版做不出真东西"。
 * 这一页存在的意义就是把这两件事同时摆出来，而不是悄悄留一张空表页假装还没轮到。
 *
 * <h2>三条独立证据</h2>
 * <ol>
 *   <li><b>附录 A 把它的承重结构判给了二期。</b>附录 A 的标题原文是
 *       「附录 A · 二期待办（首版明确不做，AI 不得顺手实现）」，其中一行是
 *       「多院区支持 | PRD 医院导航 | 依赖院区数据模型扩展」（784 行）。
 *       而 PRD 4.5.8 要管理的对象<b>就是院区</b>本身：428 行「院区列表 — 管理多院区信息」、
 *       429 行「新增院区 — 添加院区（名称、地址、地图坐标、楼层信息等）」。
 *       给它建 CRUD 等于顺手实现二期项——正撞上那句"AI 不得顺手实现"。</li>
 *   <li><b>坐标与地图被红线挡着。</b>T24 卡片的红线原文（684 行）：
 *       「不做真实地图（二期做）；首版仅模拟」。也就是说即使把"地图坐标"这一列建出来，
 *       它能承接的也只有假坐标，而假坐标会被患者当成真路线。</li>
 *   <li><b>平面图需要图片通道，全系统没有。</b>PRD 256 行「医院导航 — 展示院区平面图」，
 *       卡片 679 行同样写"展示院区平面图"。上传通道在 T23 已逐条证过不存在
 *       （医生头像、病案证件照片、文章封面图都是因此不做的），所以平面图这一项无源。</li>
 * </ol>
 *
 * <h2>后端状态</h2>
 * 这五样（院区/坐标/楼层/平面图）对应的表、实体、端点<b>一个都没建</b>。
 * 本卡的端点清单测试把 {@code navigation}、{@code campus}、{@code floor} 三个词
 * 写进了 forbidden 集合——将来谁顺手加一把，那条测试就红，红在这里是有意的。
 *
 * <h2>要补上它需要什么</h2>
 * 一次院区数据模型的扩展（新表 + 科室/医生上的院区归属列 + 排班跟着分院区），
 * 这属于附录 A 那一行说的"依赖院区数据模型扩展"，不是一页后台表单能带过的量。
 */
export default function HospitalNavigationPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="医院导航管理"
        description="PRD 4.5.8 · 首版不做，理由见下（这一页不放假数据、不放假地图）"
      />

      <div className="space-y-4 rounded-lg border bg-card p-5">
        <section className="space-y-2">
          <h2 className="text-sm font-semibold">规格要的是什么（原文）</h2>
          <Quote>PRD 428 行：院区列表 — 管理多院区信息</Quote>
          <Quote>PRD 429 行：新增院区 — 添加院区（名称、地址、地图坐标、楼层信息等）</Quote>
          <Quote>任务卡 743 行：医院导航管理：CRUD</Quote>
        </section>

        <section className="space-y-2">
          <h2 className="text-sm font-semibold">为什么首版不做</h2>
          <ol className="nav-reasons list-decimal space-y-2 pl-5 text-sm">
            <li>
              附录 A「二期待办（首版明确不做，AI 不得顺手实现）」里有一行
              「多院区支持 —— PRD 医院导航 —— 依赖院区数据模型扩展」。4.5.8 管理的对象正是院区，
              建它的 CRUD 就是去实现那一行二期项。
            </li>
            <li>
              卡片 684 行红线：「不做真实地图（二期做）；首版仅模拟」。「地图坐标」这一栏只能收假数据，
              而假坐标对患者是误导。
            </li>
            <li>
              「展示院区平面图」（PRD 256 行、卡片 679 行）需要图片上传通道，全系统没有
              ——与医生头像、病案证件照片、文章封面图同一条理由。
            </li>
          </ol>
        </section>

        <section className="space-y-2">
          <h2 className="text-sm font-semibold">现在去哪里看同类信息</h2>
          <ul className="nav-alternatives list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li>医院整体介绍：医院简介管理（PRD 4.5.9，本卡已交付）</li>
            <li>科室与医生：科室管理 / 医生管理（PRD 4.5.2、4.5.1，本卡已交付）</li>
            <li>
              楼层与院区分布：首版无处可存也无处可显示，小程序侧的「医院导航」入口同样未实现
              （属 T24 那一族，当时按同三条证据未做）
            </li>
          </ul>
        </section>

        <section className="space-y-1">
          <h2 className="text-sm font-semibold">要补上它的前置</h2>
          <p className="text-sm text-muted-foreground">
            院区数据模型：新建院区表，并给科室、医生、排班加上院区归属，之后这五栏
            （名称、地址、坐标、楼层、平面图）才有主语。这是一次跨卡改造，不是本页的一个表单。
          </p>
        </section>
      </div>
    </div>
  )
}

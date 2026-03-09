<template>
  <div class="flow-layout">
    <section class="stat-grid">
      <article v-for="item in metrics" :key="item.label" class="stat-card">
        <p class="stat-label">{{ item.label }}</p>
        <p class="stat-value">{{ item.value }}</p>
        <p class="stat-note">{{ item.note }}</p>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>当前角色操作面板</h3>
      </div>
      <div class="hint-box">
        <p><strong>当前入口：</strong>{{ roleLabel }}</p>
        <p><strong>职责重点：</strong>{{ roleFocus }}</p>
        <p class="muted">{{ roleTip }}</p>
      </div>
    </section>

    <section v-if="isProvider" class="panel">
      <div class="panel-head">
        <h3>供应方资产发布</h3>
        <button class="btn" type="button" @click="publishProviderAsset" :disabled="publishAssetLoading">
          {{ publishAssetLoading ? '发布中...' : '发布资产（供应方）' }}
        </button>
      </div>
      <div class="hint-box">
        <p>
          <strong>策略ID来源：</strong>来自运营方策略中心（`/api/policies`），资产发布时会把策略ID写入 Offer，用于消费者协商时绑定策略。
        </p>
        <p class="muted">建议先创建策略，再发布资产；发布成功后会自动刷新目录，展示“资产已上架”。</p>
      </div>
      <div class="inline-actions wrap">
        <button class="btn" type="button" @click="loadPolicies" :disabled="policyLoading">
          {{ policyLoading ? '加载中...' : '刷新策略列表' }}
        </button>
        <button class="btn" type="button" @click="createProviderPolicy" :disabled="policyCreateLoading">
          {{ policyCreateLoading ? '创建中...' : '创建默认策略' }}
        </button>
      </div>
      <div class="form-row transfer-form">
        <label>
          资产名称
          <input v-model.trim="providerAssetName" type="text" />
        </label>
        <label>
          资产分类
          <input v-model.trim="providerAssetClassification" type="text" />
        </label>
        <label>
          策略ID
          <select v-model="providerPolicyId">
            <option v-if="!policyItems.length" value="policy-basic">policy-basic（默认）</option>
            <option v-for="policy in policyItems" :key="policy.id" :value="policy.id">
              {{ policy.id }}（{{ policy.type }}）
            </option>
          </select>
        </label>
      </div>
      <div class="form-row transfer-form">
        <label>
          归属方
          <input v-model.trim="providerOwnerId" type="text" />
        </label>
        <label>
          资产描述
          <input v-model.trim="providerAssetDescription" type="text" />
        </label>
        <label>
          元数据-行业
          <input v-model.trim="providerMetadataDomain" type="text" />
        </label>
        <label>
          元数据-用途
          <input v-model.trim="providerMetadataPurpose" type="text" />
        </label>
      </div>
      <p class="muted">策略ID（可手工覆盖）</p>
      <input v-model.trim="providerPolicyId" type="text" />
      <p v-if="publishAssetMessage" class="guide-msg">{{ publishAssetMessage }}</p>
      <div class="grid-2 trace-grid">
        <div class="detail-block">
          <p class="muted">当前策略规则</p>
          <pre class="json-view">{{ selectedProviderPolicyRules }}</pre>
        </div>
        <div class="timeline-list">
          <article v-for="event in publishEvents" :key="event.id" class="timeline-item" :class="event.status.toLowerCase()">
            <p class="timeline-title">{{ event.stage }}</p>
            <p class="muted">{{ event.detail }}</p>
            <p class="mono">{{ event.time }}</p>
          </article>
          <p v-if="publishEvents.length === 0" class="muted">发布资产后，这里会显示“创建资产 -> 写入 Offer -> 刷新目录”的同步流程。</p>
        </div>
      </div>
    </section>

    <section v-if="showGuide" :class="['panel', { focused: focusSection === 'guide' }]">
      <div class="panel-head">
        <h3>演示向导（公司宣讲版）</h3>
        <div class="inline-actions">
          <button class="btn" type="button" @click="prevGuideStep" :disabled="guideStepIndex === 0 || guideRunning">
            上一步
          </button>
          <button class="btn" type="button" @click="nextGuideStep" :disabled="guideStepIndex === guideSteps.length - 1 || guideRunning">
            下一步
          </button>
          <button class="btn solid" type="button" @click="executeCurrentGuideStep" :disabled="guideRunning">
            {{ guideRunning ? '执行中...' : currentGuideStep.actionLabel }}
          </button>
          <button class="btn" type="button" @click="openGuideRunConfirm" :disabled="guideRunning">
            一键演示全流程
          </button>
        </div>
      </div>

      <div class="guide-grid">
        <div class="guide-steps">
          <button
            v-for="(step, index) in guideSteps"
            :key="step.id"
            type="button"
            class="guide-step-chip"
            :class="{ active: guideStepIndex === index }"
            @click="guideStepIndex = index"
          >
            <p class="muted">STEP {{ index + 1 }}</p>
            <p>{{ step.title }}</p>
          </button>
        </div>
        <div class="detail-block">
          <p><strong>当前步骤：</strong>{{ currentGuideStep.title }}</p>
          <p><strong>演示目标：</strong>{{ currentGuideStep.goal }}</p>
          <p class="muted">{{ currentGuideStep.talkTrack }}</p>
          <p v-if="guideMessage" class="guide-msg">{{ guideMessage }}</p>
          <pre v-if="guideBillingOutput" class="json-view">{{ guideBillingOutput }}</pre>
        </div>
      </div>
    </section>

    <section v-if="isProvider || isConsumer" class="grid-2">
      <article :class="['panel', { focused: focusSection === 'identity' }]">
        <div class="panel-head">
          <h3>认证人与目录</h3>
          <div class="inline-actions">
            <button class="btn" type="button" @click="loadIdentity" :disabled="identityLoading">
              {{ identityLoading ? '读取中...' : '刷新认证人' }}
            </button>
            <button class="btn" type="button" @click="loadCatalog" :disabled="catalogLoading">
              {{ catalogLoading ? '刷新中...' : '刷新目录' }}
            </button>
          </div>
        </div>

        <div class="info-grid">
          <label>
            参与方 ID（{{ isProvider ? '供应方：华东车联' : '消费者关注供应方' }}）
            <input v-model.trim="participantId" type="text" />
          </label>
          <div class="kv-card">
            <p class="muted">认证人 DID</p>
            <p class="mono">{{ identityDid || '-' }}</p>
          </div>
        </div>

        <p v-if="catalogError" class="error-text">{{ catalogError }}</p>

        <table v-if="catalogItems.length" class="table">
          <thead>
            <tr>
              <th>资产ID</th>
              <th>名称</th>
              <th>分类</th>
              <th>主策略</th>
              <th>套餐数</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in pagedCatalogItems"
              :key="item.asset.id"
              :class="{ selected: selectedAssetId === item.asset.id }"
              class="clickable-row"
              @click="selectAsset(item.asset.id)"
            >
              <td class="mono">{{ item.asset.id }}</td>
              <td>{{ item.asset.name }}</td>
              <td>{{ item.asset.classification }}</td>
              <td class="mono">{{ primaryOffer(item)?.policyId || '-' }}</td>
              <td>{{ item.offers.length }}</td>
              <td>{{ formatDateTime(catalogTime(item)) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="catalogItems.length" class="pager">
          <button class="btn" type="button" @click="catalogPage -= 1" :disabled="catalogPage <= 1">上一页</button>
          <p class="muted">第 {{ catalogPage }} / {{ catalogPageCount }} 页 · 共 {{ sortedCatalogItems.length }} 条</p>
          <button class="btn" type="button" @click="catalogPage += 1" :disabled="catalogPage >= catalogPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无目录数据，先执行场景或双平面演示。</p>
      </article>

      <article :class="['panel', { focused: focusSection === 'asset' }]">
        <div class="panel-head">
          <h3>{{ isConsumer ? '资产详情与创建合同' : '资产详情（供应方视角）' }}</h3>
          <button class="btn" type="button" @click="reloadSelectedAsset" :disabled="!selectedAssetId || assetLoading">
            {{ assetLoading ? '加载中...' : '刷新详情' }}
          </button>
        </div>

        <div v-if="selectedCatalog" class="detail-block">
          <p><strong>资产名称：</strong>{{ selectedCatalog.asset.name }}</p>
          <p><strong>描述：</strong>{{ selectedCatalog.asset.description }}</p>
          <p><strong>归属：</strong>{{ selectedCatalog.asset.ownerId }}</p>
          <p><strong>当前选择 Offer：</strong><span class="mono">{{ selectedOfferId || '-' }}</span></p>
          <pre class="json-view">{{ prettyJson(selectedCatalog.asset.metadata) }}</pre>
        </div>
        <p v-else class="muted">请选择一个资产查看详情。</p>

        <div v-if="isConsumer && selectedCatalog" class="detail-block">
          <p class="muted">资产策略套餐（Offers）</p>
          <table class="table">
            <thead>
              <tr>
                <th>Offer ID</th>
                <th>Policy</th>
                <th>Provider</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="offer in selectedCatalogOffers" :key="offer.id">
                <td class="mono">{{ offer.id }}</td>
                <td class="mono">{{ offer.policyId }}</td>
                <td>{{ offer.providerId }}</td>
                <td>{{ formatDateTime(offer.createdAt) }}</td>
                <td>
                  <button class="link-btn" type="button" @click="useCatalogOffer(offer.id)">选择该套餐</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="isConsumer" class="form-row">
          <label>
            消费者 ID（保险风控中心）
            <input v-model.trim="consumerId" type="text" />
          </label>
          <label>
            Offer ID（协商入参）
            <input v-model.trim="selectedOfferId" type="text" />
          </label>
        </div>
        <div v-if="isConsumer" class="hint-box">
          <p>
            目录可用 Offer：
            <span class="mono">{{ selectedCatalogOffers.length }}</span>
            <span class="state-chip" :class="offerMatch ? 'ok' : 'err'">{{ offerMatch ? '匹配' : '不匹配' }}</span>
          </p>
          <p>
            当前 Offer 绑定策略：
            <span class="mono">{{ selectedOfferPolicyId || '-' }}</span>
          </p>
          <div class="inline-actions">
            <button class="btn" type="button" @click="useLatestCatalogOffer" :disabled="!selectedCatalogOffers.length">
              使用最新 Offer
            </button>
          </div>
        </div>

        <button
          v-if="isConsumer"
          class="btn solid"
          type="button"
          @click="createNegotiation"
          :disabled="negotiationLoading || !selectedAssetId || !selectedOfferId"
        >
          {{ negotiationLoading ? '创建中...' : '创建合同协商' }}
        </button>
        <p v-else class="muted">供应方主要负责资产发布与交付追踪，不在此页面发起协商。</p>

        <p v-if="negotiationError" class="error-text">{{ negotiationError }}</p>
        <p v-if="negotiationMessage" class="guide-msg">{{ negotiationMessage }}</p>
        <div v-if="isConsumer" class="hint-box">
          <p><strong>协商治理校验：</strong>{{ negotiationGovernanceMessage || '待执行' }}</p>
          <p>
            会员记录：
            <span class="mono">{{ negotiationMembership?.id || '-' }}</span>
            <span v-if="negotiationMembership">（{{ negotiationMembership.level }} / {{ negotiationMembership.status }}）</span>
          </p>
          <p>
            签发资格：
            <span class="mono">{{ negotiationQualification?.qualified ? '已具备' : '未具备' }}</span>
            <span v-if="negotiationQualification?.credentialId">（cred={{ negotiationQualification.credentialId }}）</span>
          </p>
          <p>
            协商计费（{{ negotiationUsageCode() }}）：
            <span class="mono">
              used={{ negotiationUsage?.usedCount ?? '-' }} / limit={{ negotiationUsage?.quotaLimit ?? '-' }} / remaining={{ negotiationUsage?.remainingCount ?? '-' }}
            </span>
          </p>
        </div>
        <div v-if="latestNegotiation" class="hint-box">
          <p>最新协商：<span class="mono">{{ latestNegotiation.negotiationId }}</span></p>
          <p>协商Offer：<span class="mono">{{ latestNegotiation.offerId || '-' }}</span></p>
          <p>
            协商合同：
            <span class="mono">{{ latestNegotiation.agreementId || '-' }}</span>
          </p>
        </div>
      </article>
    </section>

    <section v-if="isConsumer" class="panel">
      <div class="panel-head">
        <h3>签发与签约流程（消费者）</h3>
        <button class="btn solid" type="button" @click="runConsumerSigningFlow" :disabled="signingFlowRunning || !selectedAssetId">
          {{ signingFlowRunning ? '执行中...' : '执行签发并签约' }}
        </button>
      </div>
      <p class="muted">流程：Issuer签发凭证 -> Identity入库凭证 -> DCP展示 -> DCP校验 -> 创建合同协商。</p>
      <div class="hint-box">
        <p>Issuer签发ID：<span class="mono">{{ issuerIssuanceId || '-' }}</span></p>
        <p>Identity凭证ID：<span class="mono">{{ signingCredentialId || '-' }}</span></p>
        <p>DCP展示ID：<span class="mono">{{ signingPresentationId || '-' }}</span></p>
      </div>
      <p v-if="signingFlowMessage" class="guide-msg">{{ signingFlowMessage }}</p>
      <div class="timeline-list">
        <article v-for="event in signingEvents" :key="event.id" class="timeline-item" :class="event.status.toLowerCase()">
          <p class="timeline-title">{{ event.stage }}</p>
          <p class="muted">{{ event.detail }}</p>
          <p class="mono">{{ event.time }}</p>
        </article>
        <p v-if="signingEvents.length === 0" class="muted">点击“执行签发并签约”后会显示每个步骤的状态。</p>
      </div>
    </section>

    <section v-if="isProvider || isConsumer" class="grid-2">
      <article :class="['panel', { focused: focusSection === 'contract' }]">
        <div class="panel-head">
          <h3>协商列表</h3>
          <button class="btn" type="button" @click="loadNegotiations" :disabled="negotiationsLoading">
            {{ negotiationsLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>

        <table v-if="negotiations.length" class="table">
          <thead>
            <tr>
              <th>协商ID</th>
              <th>资产</th>
              <th>Offer</th>
              <th>消费者</th>
              <th>状态</th>
              <th>协议ID</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedNegotiations" :key="item.negotiationId">
              <td class="mono">{{ item.negotiationId }}</td>
              <td class="mono">{{ item.assetId }}</td>
              <td class="mono">{{ item.offerId || '-' }}</td>
              <td>{{ item.consumerId }}</td>
              <td><span class="state-chip" :class="negotiationStateClass(item.state)">{{ item.state }}</span></td>
              <td class="mono">{{ item.agreementId || '-' }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="negotiations.length" class="pager">
          <button class="btn" type="button" @click="negotiationPage -= 1" :disabled="negotiationPage <= 1">上一页</button>
          <p class="muted">第 {{ negotiationPage }} / {{ negotiationPageCount }} 页 · 共 {{ sortedNegotiations.length }} 条</p>
          <button class="btn" type="button" @click="negotiationPage += 1" :disabled="negotiationPage >= negotiationPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无协商数据。</p>
      </article>

      <article :class="['panel', { focused: focusSection === 'contract' }]">
        <div class="panel-head">
          <h3>协商合同（协议）</h3>
          <button class="btn" type="button" @click="loadAgreements" :disabled="agreementsLoading">
            {{ agreementsLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>

        <table v-if="agreements.length" class="table">
          <thead>
            <tr>
              <th>协议ID</th>
              <th>资产</th>
              <th>Offer</th>
              <th>消费者</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedAgreements" :key="item.agreementId">
              <td class="mono">{{ item.agreementId }}</td>
              <td class="mono">{{ item.assetId }}</td>
              <td class="mono">{{ item.offerId || '-' }}</td>
              <td>{{ item.consumerId }}</td>
              <td>{{ item.status }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td>
                <button class="link-btn" type="button" @click="useAgreement(item.agreementId)">用于传输</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="agreements.length" class="pager">
          <button class="btn" type="button" @click="agreementPage -= 1" :disabled="agreementPage <= 1">上一页</button>
          <p class="muted">第 {{ agreementPage }} / {{ agreementPageCount }} 页 · 共 {{ sortedAgreements.length }} 条</p>
          <button class="btn" type="button" @click="agreementPage += 1" :disabled="agreementPage >= agreementPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无协议数据。</p>
      </article>
    </section>

    <section v-if="isProvider || isConsumer" :class="['panel', { focused: focusSection === 'transfer' }]">
      <div class="panel-head">
        <h3>数据传输状态与链路追踪</h3>
        <div class="inline-actions">
          <button class="btn" type="button" @click="loadTransferStatuses" :disabled="transferLoading">
            {{ transferLoading ? '刷新中...' : '刷新状态' }}
          </button>
          <button v-if="isConsumer" class="btn solid" type="button" @click="startTransfer" :disabled="startTransferLoading">
            {{ startTransferLoading ? '发起中...' : '发起传输' }}
          </button>
        </div>
      </div>

      <div v-if="isConsumer" class="form-row transfer-form">
        <label>
          协议ID
          <input v-model.trim="selectedAgreementId" type="text" placeholder="agr-xxxx" />
        </label>
        <label>
          目标数据平面
          <select v-model="selectedDataPlaneId">
            <option value="">自动分配</option>
            <option value="dp-1">dp-1</option>
            <option value="dp-2">dp-2</option>
          </select>
        </label>
        <label>
          拉取消息
          <input v-model.trim="pullMessage" type="text" placeholder="verify" />
        </label>
      </div>

      <div v-if="isConsumer" class="hint-box">
        <p><strong>Control Plane 编排门禁预演：</strong>拿到 agreementId 后先做门禁检查与 Data Plane 选择演示（只读，不扣费）。</p>
        <div class="form-row transfer-form">
          <label>
            演示调用方（X-Participant-Id）
            <input v-model.trim="orchestrationParticipantId" type="text" placeholder="operator" />
          </label>
          <label>
            运营权限令牌（X-Operator-Token）
            <input v-model.trim="orchestrationToken" type="password" placeholder="operator-demo-key" />
          </label>
        </div>
        <div class="inline-actions">
          <button class="btn" type="button" @click="previewTransferOrchestration" :disabled="orchestrationPreviewLoading || !selectedAgreementId">
            {{ orchestrationPreviewLoading ? '预演中...' : '演示编排门禁（Agreement）' }}
          </button>
        </div>
        <p v-if="orchestrationPreviewError" class="error-text">{{ orchestrationPreviewError }}</p>
        <div v-if="orchestrationPreview" class="detail-block">
          <p>
            <strong>预演结论：</strong>
            <span class="state-chip" :class="orchestrationPreview.readyToTransfer ? 'ok' : 'err'">
              {{ orchestrationPreview.readyToTransfer ? '可发起真实传输' : '门禁未通过' }}
            </span>
          </p>
          <p><strong>建议 Data Plane：</strong><span class="mono">{{ orchestrationPreview.selectedDataPlaneId || '-' }}</span></p>
          <p><strong>选择方式：</strong>{{ orchestrationPreview.selectedBy || '-' }}</p>
          <p><strong>预演时间：</strong>{{ formatDateTime(orchestrationPreview.generatedAt) }}</p>
          <p class="muted">{{ orchestrationPreview.message }}</p>
          <table v-if="orchestrationPreview.steps.length" class="table">
            <thead>
              <tr>
                <th>步骤</th>
                <th>结果</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="step in orchestrationPreview.steps" :key="step.stepCode">
                <td>{{ step.stepName }}</td>
                <td><span class="state-chip" :class="step.passed ? 'ok' : 'err'">{{ step.passed ? '通过' : '失败' }}</span></td>
                <td>{{ step.detail }}</td>
              </tr>
            </tbody>
          </table>
          <p class="muted">推荐请求体（可直接用于 `POST /api/transfers`）：</p>
          <pre class="json-view">{{ prettyJson(orchestrationPreview.suggestedTransferRequest) }}</pre>
        </div>
      </div>

      <p v-if="transferError" class="error-text">{{ transferError }}</p>
      <div v-if="isConsumer" class="hint-box">
        <p><strong>传输治理校验：</strong>{{ transferGovernanceMessage || '待执行' }}</p>
        <p>
          会员记录：
          <span class="mono">{{ transferMembership?.id || '-' }}</span>
          <span v-if="transferMembership">（{{ transferMembership.level }} / {{ transferMembership.status }}）</span>
        </p>
        <p>
          签发资格：
          <span class="mono">{{ transferQualification?.qualified ? '已具备' : '未具备' }}</span>
          <span v-if="transferQualification?.presentationId">（vp={{ transferQualification.presentationId }}）</span>
        </p>
        <p>
          传输计费（{{ transferUsageCode() }}）：
          <span class="mono">used={{ transferUsage?.usedCount ?? '-' }} / limit={{ transferUsage?.quotaLimit ?? '-' }} / remaining={{ transferUsage?.remainingCount ?? '-' }}</span>
        </p>
      </div>

      <table v-if="transferStatuses.length" class="table">
        <thead>
          <tr>
            <th>传输ID</th>
            <th>协议ID</th>
            <th>Data Plane</th>
            <th>控制面状态</th>
            <th>数据面状态</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in pagedTransferStatuses"
            :key="item.transferProcessId"
            :class="{ selected: selectedTransferId === item.transferProcessId }"
          >
            <td class="mono">{{ item.transferProcessId }}</td>
            <td class="mono">{{ item.agreementId }}</td>
            <td>{{ item.dataPlaneId }}</td>
            <td>{{ item.controlState }}</td>
            <td>{{ item.dataPlaneState || '-' }}</td>
            <td>{{ formatDateTime(item.updatedAt || item.createdAt) }}</td>
            <td>
              <button class="link-btn" type="button" @click="inspectTransfer(item.transferProcessId)">查看链路</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="transferStatuses.length" class="pager">
        <button class="btn" type="button" @click="transferPage -= 1" :disabled="transferPage <= 1">上一页</button>
        <p class="muted">第 {{ transferPage }} / {{ transferPageCount }} 页 · 共 {{ sortedTransferStatuses.length }} 条</p>
        <button class="btn" type="button" @click="transferPage += 1" :disabled="transferPage >= transferPageCount">下一页</button>
      </div>
      <p v-else class="muted">暂无传输流程。</p>

      <div class="grid-2 trace-grid">
        <div class="detail-block">
          <p class="muted">当前追踪传输</p>
          <p class="mono">{{ selectedTransferId || '-' }}</p>
          <div v-if="currentEdr" class="hint-box">
            <p><strong>EDR endpoint：</strong><span class="mono">{{ currentEdr.endpoint }}</span></p>
            <p><strong>EDR token：</strong><span class="mono">{{ currentEdr.authToken }}</span></p>
            <button v-if="isConsumer" class="btn" type="button" @click="pullTransferData" :disabled="pullingData">
              {{ pullingData ? '拉取中...' : '拉取数据' }}
            </button>
          </div>
          <pre v-if="pulledPayload" class="json-view">{{ pulledPayload }}</pre>
        </div>

        <div class="detail-block">
          <p class="muted">全链路轨迹</p>
          <pre class="json-view">{{ transferTraceText }}</pre>
        </div>
      </div>
    </section>

    <section v-if="isProvider" :class="['panel', { focused: focusSection === 'dual' }]">
      <div class="panel-head">
        <h3>双数据平面传输演示（dp-1 / dp-2）</h3>
        <div class="inline-actions">
          <button class="btn" type="button" @click="runDualPlaneDemo" :disabled="dualDemoRunning">
            {{ dualDemoRunning ? '执行中...' : '生成双平面流程' }}
          </button>
          <button class="btn solid" type="button" @click="playDualPlaneDemo" :disabled="dualPlayRunning">
            {{ dualPlayRunning ? '演示中...' : '演示传输过程' }}
          </button>
        </div>
      </div>

      <p v-if="dualError" class="error-text">{{ dualError }}</p>

      <div v-if="dualDemoResult" class="dual-grid">
        <article v-for="step in dualDemoResult.steps" :key="step.transferProcessId" class="step-card">
          <p class="muted">{{ step.dataPlaneId }}</p>
          <p class="mono">{{ step.transferProcessId }}</p>
          <p>资产：<span class="mono">{{ step.assetId }}</span></p>
          <p>协议：<span class="mono">{{ step.agreementId }}</span></p>
        </article>
      </div>

      <div class="grid-2">
        <div class="timeline-list">
          <article v-for="event in dualEvents" :key="event.id" class="timeline-item" :class="event.status.toLowerCase()">
            <p class="timeline-title">{{ event.stage }}</p>
            <p class="muted">{{ event.detail }}</p>
            <p class="mono">{{ event.time }}</p>
          </article>
        </div>

        <div class="payload-list">
          <article v-for="(payload, transferId) in dualPayloads" :key="transferId" class="detail-block">
            <p class="muted">传输结果 {{ transferId }}</p>
            <pre class="json-view">{{ payload }}</pre>
          </article>
          <p v-if="Object.keys(dualPayloads).length === 0" class="muted">还没有演示输出数据。</p>
        </div>
      </div>
    </section>

    <div v-if="showGuideConfirm" class="modal-mask" @click.self="showGuideConfirm = false">
      <section class="modal-card">
        <div class="panel-head">
          <h3>确认执行一键全流程演示</h3>
          <button class="btn" type="button" @click="showGuideConfirm = false">取消</button>
        </div>
        <p class="muted">系统将依次自动执行以下步骤，请确认环境可用（服务健康为 UP）。</p>
        <div class="timeline-list">
          <article v-for="(step, index) in guideSteps" :key="step.id" class="step-card">
            <p class="timeline-title">STEP {{ index + 1 }} · {{ step.title }}</p>
            <p><strong>目标：</strong>{{ step.goal }}</p>
            <p class="muted">{{ step.talkTrack }}</p>
          </article>
        </div>
        <div class="inline-actions">
          <button class="btn solid" type="button" @click="confirmGuideRun" :disabled="guideRunning">确认并开始</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CONTROL_BASE, IDENTITY_BASE, ISSUER_BASE, OPERATOR_BASE, prettyJson, requestJson } from '../lib/http'

type JsonObject = Record<string, unknown>

type CatalogAsset = {
  id: string
  name: string
  description: string
  classification: string
  ownerId: string
  metadata: JsonObject
}

type CatalogOffer = {
  id: string
  assetId: string
  policyId: string
  providerId: string
  createdAt: string
}

type CatalogEntry = {
  datasetId: string
  asset: CatalogAsset
  offers: CatalogOffer[]
}

type PolicyItem = {
  id: string
  type: string
  rules: JsonObject
}

type PolicyListResponse = {
  items: PolicyItem[]
}

type MembershipItem = {
  id: string
  participantId: string
  level: string
  validFrom: string
  validTo: string | null
  status: string
}

type BillingUsageStatus = {
  participantId: string
  serviceCode: string
  allowed: boolean
  usedCount: number
  quotaLimit: number
  remainingCount: number
  unitPrice: string | number
  estimatedAmount: string | number
  checkedAt: string
}

type QualificationStatus = {
  participantId: string
  audience: string | null
  qualified: boolean
  reason: string
  credentialId: string | null
  presentationId: string | null
  credentialType: string | null
  issuer: string | null
  verifiedAt: string | null
  expiresAt: string | null
}

type NegotiationItem = {
  negotiationId: string
  agreementId: string | null
  assetId: string
  consumerId: string
  offerId: string | null
  policyId: string
  state: string
  createdAt: string
}

type AgreementItem = {
  agreementId: string
  negotiationId: string
  assetId: string
  offerId: string | null
  consumerId: string
  providerId: string
  status: string
  validFrom: string
  validTo: string
  createdAt: string
}

type TransferProcessResponse = {
  id: string
  agreementId: string
  protocol: string
  dataPlaneId: string
  state: string
  createdAt: string
}

type TransferStatus = {
  transferProcessId: string
  agreementId: string
  dataPlaneId: string
  controlState: string
  dataPlaneState: string | null
  edrEndpoint: string | null
  dataPlaneEdrEndpoint: string | null
  createdAt: string
  updatedAt: string
  dataPlaneUpdatedAt: string | null
  dataPlaneEdrExpiresAt: string | null
}

type TransferEdr = {
  transferProcessId: string
  endpoint: string
  authKey: string
  authToken: string
  updatedAt: string
}

type TransferTrace = {
  transfer?: TransferStatus
  agreement?: JsonObject
  negotiation?: JsonObject
  asset?: JsonObject
  dataPlane?: JsonObject
  edr?: JsonObject
}

type OrchestrationStep = {
  stepCode: string
  stepName: string
  passed: boolean
  detail: string
  snapshot: JsonObject
}

type OrchestrationPreview = {
  agreementId: string
  consumerId: string
  providerId: string
  requestedDataPlaneId: string | null
  selectedDataPlaneId: string | null
  selectedDataPlaneProtocol: string | null
  selectedBy: string | null
  readyToTransfer: boolean
  message: string
  suggestedTransferRequest: JsonObject
  steps: OrchestrationStep[]
  generatedAt: string
}

type DualPlaneStep = {
  dataPlaneId: string
  assetId: string
  negotiationId: string
  agreementId: string
  transferProcessId: string
}

type DualPlaneDemoResult = {
  runId: string
  consumerId: string
  transferIds: string[]
  steps: DualPlaneStep[]
  transferStatuses: TransferStatus[]
}

type TimelineEvent = {
  id: string
  stage: string
  detail: string
  time: string
  status: 'RUNNING' | 'OK' | 'ERROR'
}

type ViewMode = 'provider' | 'consumer' | 'full'

type GuideFocus = 'guide' | 'identity' | 'asset' | 'contract' | 'transfer' | 'dual'

type GuideStep = {
  id: string
  title: string
  goal: string
  talkTrack: string
  actionLabel: string
  focus: GuideFocus
}

const props = withDefaults(
  defineProps<{
    mode?: ViewMode
  }>(),
  {
    mode: 'full'
  }
)

const mode = computed<ViewMode>(() => props.mode)
const isProvider = computed(() => mode.value === 'provider' || mode.value === 'full')
const isConsumer = computed(() => mode.value === 'consumer' || mode.value === 'full')
const showGuide = computed(() => mode.value !== 'consumer')

const participantId = ref('participant-a')
const consumerId = ref('participant-b')
const selectedOfferId = ref('')
const selectedAssetId = ref('')
const selectedAgreementId = ref('')
const selectedDataPlaneId = ref('')
const selectedTransferId = ref('')
const pullMessage = ref('verify')
const orchestrationParticipantId = ref('operator')
const orchestrationToken = ref('operator-demo-key')

const providerAssetName = ref(`华东车联网实时数据包-${new Date().toISOString().slice(0, 10)}`)
const providerAssetDescription = ref('包含车辆轨迹、速度、异常事件的分钟级数据。')
const providerAssetClassification = ref('vehicle-telemetry')
const providerOwnerId = ref('participant-a')
const providerPolicyId = ref('policy-basic')
const providerMetadataDomain = ref('交通')
const providerMetadataPurpose = ref('风控')
const publishAssetLoading = ref(false)
const publishAssetMessage = ref('')
const policyItems = ref<PolicyItem[]>([])
const policyLoading = ref(false)
const policyCreateLoading = ref(false)
const publishEvents = ref<TimelineEvent[]>([])

const identityDid = ref('')
const catalogItems = ref<CatalogEntry[]>([])
const selectedCatalog = ref<CatalogEntry | null>(null)
const latestNegotiation = ref<NegotiationItem | null>(null)
const negotiations = ref<NegotiationItem[]>([])
const agreements = ref<AgreementItem[]>([])
const transferStatuses = ref<TransferStatus[]>([])
const transferTrace = ref<TransferTrace | null>(null)
const currentEdr = ref<TransferEdr | null>(null)
const pulledPayload = ref('')
const orchestrationPreview = ref<OrchestrationPreview | null>(null)

const dualDemoResult = ref<DualPlaneDemoResult | null>(null)
const dualEvents = ref<TimelineEvent[]>([])
const dualPayloads = ref<Record<string, string>>({})

const guideSteps: GuideStep[] = [
  {
    id: 'guide-1',
    title: '身份与目录准备',
    goal: '显示 DID，并让目录出现可讲解资产',
    talkTrack: '先证明主体身份，再加载目录资产，形成“谁提供什么数据”的起点。',
    actionLabel: '执行：刷新认证人与目录',
    focus: 'identity'
  },
  {
    id: 'guide-2',
    title: '签发并创建协商',
    goal: '先完成身份材料签发，再基于选中资产发起协商',
    talkTrack: '消费者先完成凭证签发与DCP校验，然后进入合同协商，形成可执行协议。',
    actionLabel: '执行：签发并创建协商',
    focus: 'asset'
  },
  {
    id: 'guide-3',
    title: '确认协商与协议',
    goal: '在列表中看到协商 ID 与协议 ID',
    talkTrack: '这一页用于证明“合同确实谈成了”，不是口头状态。',
    actionLabel: '执行：刷新协商与协议列表',
    focus: 'contract'
  },
  {
    id: 'guide-4',
    title: '发起传输并追踪链路',
    goal: '生成传输流程，看到全链路状态',
    talkTrack: '控制面编排，数据面执行，链路全程可追踪。',
    actionLabel: '执行：发起传输并查看链路',
    focus: 'transfer'
  },
  {
    id: 'guide-5',
    title: 'EDR 拉取数据',
    goal: '用 EDR 凭证拉取 payload',
    talkTrack: '拿到数据结果，证明协商后的协议真正落到数据交付。',
    actionLabel: '执行：拉取传输数据',
    focus: 'transfer'
  },
  {
    id: 'guide-6',
    title: '双数据平面分流',
    goal: '同时生成 dp-1 / dp-2 两条传输',
    talkTrack: '同一业务流程可以在两个数据平面并行，满足扩展与高可用。',
    actionLabel: '执行：生成双平面流程',
    focus: 'dual'
  },
  {
    id: 'guide-7',
    title: '计费校验',
    goal: '展示按次扣费与剩余额度变化',
    talkTrack: '业务调用前先进行额度校验，展示可商业化计费能力。',
    actionLabel: '执行：按次计费校验',
    focus: 'guide'
  }
]

const guideStepIndex = ref(0)
const guideRunning = ref(false)
const guideMessage = ref('')
const guideBillingOutput = ref('')
const showGuideConfirm = ref(false)

const signingFlowRunning = ref(false)
const signingFlowMessage = ref('')
const signingEvents = ref<TimelineEvent[]>([])
const issuerIssuanceId = ref('')
const signingCredentialId = ref('')
const signingPresentationId = ref('')

const identityLoading = ref(false)
const catalogLoading = ref(false)
const assetLoading = ref(false)
const negotiationLoading = ref(false)
const negotiationsLoading = ref(false)
const agreementsLoading = ref(false)
const transferLoading = ref(false)
const startTransferLoading = ref(false)
const pullingData = ref(false)
const dualDemoRunning = ref(false)
const dualPlayRunning = ref(false)
const orchestrationPreviewLoading = ref(false)

const catalogError = ref('')
const negotiationError = ref('')
const negotiationMessage = ref('')
const transferError = ref('')
const dualError = ref('')
const orchestrationPreviewError = ref('')
const negotiationMembership = ref<MembershipItem | null>(null)
const negotiationUsage = ref<BillingUsageStatus | null>(null)
const negotiationQualification = ref<QualificationStatus | null>(null)
const transferMembership = ref<MembershipItem | null>(null)
const transferUsage = ref<BillingUsageStatus | null>(null)
const transferQualification = ref<QualificationStatus | null>(null)
const negotiationGovernanceMessage = ref('')
const transferGovernanceMessage = ref('')

const PAGE_SIZE = 10
const catalogPage = ref(1)
const negotiationPage = ref(1)
const agreementPage = ref(1)
const transferPage = ref(1)

const roleLabel = computed(() => {
  if (mode.value === 'provider') {
    return '供应方（华东车联）'
  }
  if (mode.value === 'consumer') {
    return '消费方（保险风控中心）'
  }
  return '全角色联合视角'
})

const roleFocus = computed(() => {
  if (mode.value === 'provider') {
    return '发布资产、查看协商、追踪交付'
  }
  if (mode.value === 'consumer') {
    return '发现资产、协商签约、拉取数据'
  }
  return '覆盖从资产到计费的全流程演示'
})

const roleTip = computed(() => {
  if (mode.value === 'provider') {
    return '先在“供应方资产发布”创建资产，再切到协商与传输区域观察消费侧动作。'
  }
  if (mode.value === 'consumer') {
    return '先刷新目录并选中资产，然后依次创建协商、发起传输并拉取数据。'
  }
  return '可使用“演示向导”一键串联 7 步流程并按区域讲解。'
})

function primaryOffer(item: CatalogEntry): CatalogOffer | null {
  if (!item.offers.length) {
    return null
  }
  return [...item.offers].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt))[0]
}

const selectedCatalogOffers = computed(() => {
  if (!selectedCatalog.value) {
    return [] as CatalogOffer[]
  }
  return [...selectedCatalog.value.offers].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt))
})

const selectedOfferPolicyId = computed(() => {
  const offer = selectedCatalogOffers.value.find((item) => item.id === selectedOfferId.value)
  return offer?.policyId ?? ''
})

const offerMatch = computed(() => {
  if (!selectedOfferId.value) {
    return false
  }
  return selectedCatalogOffers.value.some((item) => item.id === selectedOfferId.value)
})

const selectedProviderPolicyRules = computed(() => {
  const selected = policyItems.value.find((item) => item.id === providerPolicyId.value)
  if (!selected) {
    return prettyJson({
      policyId: providerPolicyId.value,
      tip: '当前策略ID未在策略中心找到，发布时会原样写入 Offer。请先“刷新策略列表”或“创建默认策略”。'
    })
  }
  return prettyJson({
    policyId: selected.id,
    type: selected.type,
    rules: selected.rules
  })
})

function toTimeMillis(value: unknown): number {
  if (typeof value !== 'string' || !value) {
    return 0
  }
  const parsed = Date.parse(value.replace(' ', 'T'))
  return Number.isNaN(parsed) ? 0 : parsed
}

function formatDateTime(value: unknown): string {
  const ms = toTimeMillis(value)
  if (ms <= 0) {
    return '-'
  }
  return new Date(ms).toLocaleString()
}

function paginate<T>(items: T[], page: number): T[] {
  const start = (page - 1) * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

function catalogTime(item: CatalogEntry): string {
  const createdAt = item.asset.metadata?.createdAt
  return typeof createdAt === 'string' ? createdAt : ''
}

const sortedCatalogItems = computed(() => {
  return [...catalogItems.value].sort((a, b) => toTimeMillis(catalogTime(b)) - toTimeMillis(catalogTime(a)))
})

const sortedNegotiations = computed(() => {
  return [...negotiations.value].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt))
})

const sortedAgreements = computed(() => {
  return [...agreements.value].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt))
})

const sortedTransferStatuses = computed(() => {
  return [...transferStatuses.value].sort((a, b) => {
    const left = toTimeMillis(b.updatedAt || b.createdAt)
    const right = toTimeMillis(a.updatedAt || a.createdAt)
    return left - right
  })
})

const catalogPageCount = computed(() => Math.max(1, Math.ceil(sortedCatalogItems.value.length / PAGE_SIZE)))
const negotiationPageCount = computed(() => Math.max(1, Math.ceil(sortedNegotiations.value.length / PAGE_SIZE)))
const agreementPageCount = computed(() => Math.max(1, Math.ceil(sortedAgreements.value.length / PAGE_SIZE)))
const transferPageCount = computed(() => Math.max(1, Math.ceil(sortedTransferStatuses.value.length / PAGE_SIZE)))

const pagedCatalogItems = computed(() => paginate(sortedCatalogItems.value, catalogPage.value))
const pagedNegotiations = computed(() => paginate(sortedNegotiations.value, negotiationPage.value))
const pagedAgreements = computed(() => paginate(sortedAgreements.value, agreementPage.value))
const pagedTransferStatuses = computed(() => paginate(sortedTransferStatuses.value, transferPage.value))

const metrics = computed(() => {
  const assetLabel = isProvider.value ? '已发布资产' : '可选目录资产'
  return [
    { label: assetLabel, value: String(catalogItems.value.length), note: '可用资产条目' },
    { label: '协商总数', value: String(negotiations.value.length), note: '合同协商记录' },
    { label: '协商合同', value: String(agreements.value.length), note: '激活协议记录' },
    { label: '传输流程', value: String(transferStatuses.value.length), note: '控制面 + 数据面状态' }
  ]
})

const transferTraceText = computed(() => {
  if (!transferTrace.value) {
    return '请选择一个传输流程查看详细链路。'
  }
  return prettyJson(transferTrace.value)
})

const currentGuideStep = computed(() => guideSteps[guideStepIndex.value])
const focusSection = computed<GuideFocus>(() => currentGuideStep.value.focus)

function normalizeError(err: unknown): string {
  return err instanceof Error ? err.message : '请求失败'
}

function extractHttpStatus(err: unknown): number | null {
  const text = normalizeError(err)
  const match = text.match(/HTTP\\s+(\\d{3})/)
  if (!match) {
    return null
  }
  const code = Number.parseInt(match[1], 10)
  return Number.isNaN(code) ? null : code
}

function governanceFailureMessage(scene: '协商' | '传输', err: unknown): string {
  const code = extractHttpStatus(err)
  const message = normalizeError(err)
  if (code === 403) {
    if (message.includes('Credential qualification required')) {
      return `${scene}失败：消费方缺少签发资格（需先写入凭证并完成 DCP 校验）。`
    }
    return `${scene}失败：消费方缺少有效 ACTIVE 会员。`
  }
  if (code === 402) {
    return `${scene}失败：计费额度不足（按次配额耗尽）。`
  }
  if (code === 409 && scene === '协商') {
    return `${scene}失败：Offer 与资产不匹配或不存在。`
  }
  return message
}

async function queryActiveMembership(participant: string): Promise<MembershipItem> {
  const encoded = encodeURIComponent(participant)
  return requestJson<MembershipItem>(`${OPERATOR_BASE}/api/memberships/active?participantId=${encoded}`)
}

async function queryUsageStatus(participant: string, serviceCode: string): Promise<BillingUsageStatus | null> {
  try {
    const encodedParticipant = encodeURIComponent(participant)
    const encodedService = encodeURIComponent(serviceCode)
    return await requestJson<BillingUsageStatus>(
      `${OPERATOR_BASE}/api/billing/usage/status?participantId=${encodedParticipant}&serviceCode=${encodedService}`
    )
  } catch (_err) {
    return null
  }
}

async function queryQualificationStatus(participant: string, audience: string): Promise<QualificationStatus | null> {
  try {
    const encodedParticipant = encodeURIComponent(participant)
    const encodedAudience = encodeURIComponent(audience)
    return await requestJson<QualificationStatus>(
      `${IDENTITY_BASE}/api/dcp/qualification?participantId=${encodedParticipant}&audience=${encodedAudience}`
    )
  } catch (_err) {
    return null
  }
}

function resolveTransferConsumerId(): string {
  const selected = agreements.value.find((item) => item.agreementId === selectedAgreementId.value)
  if (selected?.consumerId) {
    return selected.consumerId
  }
  if (latestNegotiation.value?.consumerId) {
    return latestNegotiation.value.consumerId
  }
  return consumerId.value
}

function resolveTransferAssetId(): string {
  const selected = agreements.value.find((item) => item.agreementId === selectedAgreementId.value)
  if (selected?.assetId) {
    return selected.assetId
  }
  if (latestNegotiation.value?.assetId) {
    return latestNegotiation.value.assetId
  }
  return selectedAssetId.value
}

function negotiationUsageCode(): string {
  if (!selectedOfferId.value) {
    return 'CONTRACT_NEGOTIATION_CREATE'
  }
  return `CONTRACT_NEGOTIATION_CREATE:${selectedOfferId.value}`
}

function transferUsageCode(): string {
  const assetId = resolveTransferAssetId()
  if (!assetId) {
    return 'TRANSFER_START'
  }
  return `TRANSFER_START:${assetId}`
}

function createTimelineEvent(stage: string, detail: string, status: 'RUNNING' | 'OK' | 'ERROR'): TimelineEvent {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    stage,
    detail,
    time: new Date().toLocaleTimeString(),
    status
  }
}

function pushTimeline(stage: string, detail: string, status: 'RUNNING' | 'OK' | 'ERROR') {
  dualEvents.value = [createTimelineEvent(stage, detail, status), ...dualEvents.value].slice(0, 24)
}

function pushPublishEvent(stage: string, detail: string, status: 'RUNNING' | 'OK' | 'ERROR') {
  publishEvents.value = [createTimelineEvent(stage, detail, status), ...publishEvents.value].slice(0, 20)
}

function pushSigningEvent(stage: string, detail: string, status: 'RUNNING' | 'OK' | 'ERROR') {
  signingEvents.value = [createTimelineEvent(stage, detail, status), ...signingEvents.value].slice(0, 20)
}

function useCatalogOffer(offerId: string) {
  selectedOfferId.value = offerId
}

function useLatestCatalogOffer() {
  if (!selectedCatalogOffers.value.length) {
    return
  }
  selectedOfferId.value = selectedCatalogOffers.value[0].id
}

function negotiationStateClass(state: string): string {
  if (state.includes('REJECTED') || state.includes('FAILED') || state.includes('ERROR')) {
    return 'err'
  }
  if (state.includes('FINALIZED') || state.includes('ACTIVE') || state.includes('OK')) {
    return 'ok'
  }
  return 'pending'
}

function prevGuideStep() {
  if (guideStepIndex.value > 0) {
    guideStepIndex.value -= 1
    guideMessage.value = ''
  }
}

function nextGuideStep() {
  if (guideStepIndex.value < guideSteps.length - 1) {
    guideStepIndex.value += 1
    guideMessage.value = ''
  }
}

function openGuideRunConfirm() {
  showGuideConfirm.value = true
}

function confirmGuideRun() {
  showGuideConfirm.value = false
  void runGuideFromStart()
}

async function loadPolicies() {
  policyLoading.value = true
  try {
    const data = await requestJson<PolicyListResponse>(`${OPERATOR_BASE}/api/policies`)
    policyItems.value = Array.isArray(data.items) ? data.items : []
    if (policyItems.value.length > 0) {
      const found = policyItems.value.some((item) => item.id === providerPolicyId.value)
      if (!found) {
        providerPolicyId.value = policyItems.value[0].id
      }
    }
  } catch (err) {
    publishAssetMessage.value = `策略加载失败：${normalizeError(err)}`
  } finally {
    policyLoading.value = false
  }
}

async function createProviderPolicy() {
  policyCreateLoading.value = true
  publishAssetMessage.value = ''
  try {
    const data = await requestJson<PolicyItem>(`${OPERATOR_BASE}/api/policies`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'USAGE_LIMIT',
        rules: {
          domain: providerMetadataDomain.value || '交通',
          purpose: providerMetadataPurpose.value || '风控',
          maxCallsPerMonth: 10000,
          requireMembership: 'GOLD'
        }
      })
    })
    providerPolicyId.value = data.id
    publishAssetMessage.value = `策略创建成功：${data.id}`
    await loadPolicies()
  } catch (err) {
    publishAssetMessage.value = `策略创建失败：${normalizeError(err)}`
  } finally {
    policyCreateLoading.value = false
  }
}

async function publishProviderAsset() {
  publishAssetMessage.value = ''
  catalogError.value = ''
  publishEvents.value = []

  if (!providerAssetName.value || !providerAssetDescription.value || !providerPolicyId.value) {
    publishAssetMessage.value = '请完整填写资产名称、描述和策略ID。'
    return
  }

  publishAssetLoading.value = true
  try {
    pushPublishEvent('提交发布请求', '向控制面写入资产和 Offer。', 'RUNNING')
    const response = await requestJson<CatalogEntry>(`${CONTROL_BASE}/api/catalog/assets`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: providerAssetName.value,
        description: providerAssetDescription.value,
        classification: providerAssetClassification.value,
        ownerId: providerOwnerId.value,
        policyId: providerPolicyId.value,
        providerId: providerOwnerId.value,
        metadata: {
          domain: providerMetadataDomain.value,
          purpose: providerMetadataPurpose.value,
          producer: '华东车联'
        }
      })
    })
    const latestOffer = primaryOffer(response)

    pushPublishEvent(
      '资产与 Offer 已创建',
      `asset=${response.asset.id}，offer=${latestOffer?.id || '-'}，policy=${latestOffer?.policyId || '-'}`,
      'OK'
    )
    publishAssetMessage.value = `资产发布成功：${response.asset.name}（${response.asset.id}）`
    selectedAssetId.value = response.asset.id
    selectedOfferId.value = latestOffer?.id || ''
    participantId.value = response.asset.ownerId
    pushPublishEvent('刷新目录', '自动同步目录，验证新资产可见。', 'RUNNING')
    await Promise.all([loadCatalog(), loadNegotiations(), loadAgreements(), loadTransferStatuses()])
    await loadAssetDetail(response.asset.id)
    const found = catalogItems.value.some((item) => item.asset.id === response.asset.id)
    pushPublishEvent('目录同步完成', found ? '新资产已在目录展示，可被消费者发现。' : '目录未找到新资产，请手工刷新。', found ? 'OK' : 'ERROR')
  } catch (err) {
    pushPublishEvent('发布失败', normalizeError(err), 'ERROR')
    publishAssetMessage.value = `发布失败：${normalizeError(err)}`
  } finally {
    publishAssetLoading.value = false
  }
}

async function loadIdentity() {
  identityLoading.value = true
  try {
    const data = await requestJson<JsonObject>(`${IDENTITY_BASE}/api/identity/did`)
    identityDid.value = typeof data.did === 'string' ? data.did : ''
  } catch (err) {
    catalogError.value = normalizeError(err)
  } finally {
    identityLoading.value = false
  }
}

async function loadCatalog() {
  catalogLoading.value = true
  catalogError.value = ''

  try {
    const data = await requestJson<CatalogEntry[]>(`${CONTROL_BASE}/api/catalog`)
    catalogItems.value = data
    catalogPage.value = 1
    if (!selectedAssetId.value && sortedCatalogItems.value.length > 0) {
      selectedAssetId.value = sortedCatalogItems.value[0].asset.id
      await loadAssetDetail(sortedCatalogItems.value[0].asset.id)
    }
  } catch (err) {
    catalogError.value = normalizeError(err)
  } finally {
    catalogLoading.value = false
  }
}

async function loadAssetDetail(assetId: string) {
  assetLoading.value = true
  catalogError.value = ''

  try {
    selectedCatalog.value = await requestJson<CatalogEntry>(`${CONTROL_BASE}/api/catalog/${assetId}`)
    if (isConsumer.value && selectedCatalogOffers.value.length > 0) {
      const exists = selectedCatalogOffers.value.some((item) => item.id === selectedOfferId.value)
      if (!exists) {
        selectedOfferId.value = selectedCatalogOffers.value[0].id
      }
    }
  } catch (err) {
    catalogError.value = normalizeError(err)
    selectedCatalog.value = null
  } finally {
    assetLoading.value = false
  }
}

function selectAsset(assetId: string) {
  selectedAssetId.value = assetId
  void loadAssetDetail(assetId)
}

function reloadSelectedAsset() {
  if (!selectedAssetId.value) {
    return
  }
  void loadAssetDetail(selectedAssetId.value)
}

async function createNegotiation() {
  if (!selectedAssetId.value) {
    negotiationError.value = '请先选择资产。'
    return
  }
  if (!selectedOfferId.value) {
    negotiationError.value = '请先选择 Offer（策略套餐）。'
    return
  }

  negotiationLoading.value = true
  negotiationError.value = ''
  negotiationMessage.value = ''
  latestNegotiation.value = null
  selectedAgreementId.value = ''
  negotiationMembership.value = null
  negotiationUsage.value = null
  negotiationQualification.value = null
  negotiationGovernanceMessage.value = ''
  const negotiationParticipant = consumerId.value

  try {
    const activeMembership = await queryActiveMembership(negotiationParticipant)
    negotiationMembership.value = activeMembership
    negotiationGovernanceMessage.value = `会员校验通过：${activeMembership.id}`

    const qualification = await queryQualificationStatus(negotiationParticipant, participantId.value)
    negotiationQualification.value = qualification
    if (!qualification?.qualified) {
      const reason = qualification?.reason || 'UNKNOWN'
      throw new Error(`HTTP 403: Credential qualification required, reason=${reason}`)
    }
    negotiationGovernanceMessage.value = `${negotiationGovernanceMessage.value}；签发资格校验通过。`

    const response = await requestJson<NegotiationItem>(`${CONTROL_BASE}/api/contracts/negotiations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        assetId: selectedAssetId.value,
        consumerId: consumerId.value,
        offerId: selectedOfferId.value
      })
    })
    latestNegotiation.value = response
    selectedAgreementId.value = response.agreementId ?? ''
    if (response.state.includes('REJECTED') || !response.agreementId) {
      negotiationMessage.value = `协商失败：${response.state}，请确认 offerId 是否属于当前资产。`
    } else {
      negotiationMessage.value = `协商成功：neg=${response.negotiationId}，offer=${response.offerId}，agr=${response.agreementId}`
    }
    negotiationGovernanceMessage.value = `${negotiationGovernanceMessage.value}；计费扣减已执行。`

    await Promise.all([loadNegotiations(), loadAgreements()])
  } catch (err) {
    negotiationError.value = governanceFailureMessage('协商', err)
    negotiationMessage.value = '协商请求被拒绝，请查看上方治理校验与错误信息。'
    if (!negotiationGovernanceMessage.value) {
      negotiationGovernanceMessage.value = negotiationError.value
    }
    latestNegotiation.value = null
    selectedAgreementId.value = ''
    await loadNegotiations()
  } finally {
    negotiationUsage.value = await queryUsageStatus(negotiationParticipant, negotiationUsageCode())
    if (negotiationUsage.value) {
      negotiationGovernanceMessage.value = `${negotiationGovernanceMessage.value || '治理检查完成'}；计费剩余 ${negotiationUsage.value.remainingCount}`
    }
    negotiationLoading.value = false
  }
}

async function runConsumerSigningFlow() {
  if (!selectedAssetId.value) {
    signingFlowMessage.value = '请先在目录中选择资产。'
    return
  }

  signingFlowRunning.value = true
  signingFlowMessage.value = ''
  signingEvents.value = []
  issuerIssuanceId.value = ''
  signingCredentialId.value = ''
  signingPresentationId.value = ''

  try {
    pushSigningEvent('步骤1：Issuer签发', '向 Issuer Service 请求签发商业凭证。', 'RUNNING')
    const issuance = await requestJson<Record<string, unknown>>(`${ISSUER_BASE}/api/issuer/credentials`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': consumerId.value
      },
      body: JSON.stringify({
        type: 'CommercialAccessCredential',
        issuer: 'issuer-A',
        claims: { participant: consumerId.value, purpose: '风控取数', level: 'STANDARD' },
        expiresAt: '2026-12-31T00:00:00Z'
      })
    })
    issuerIssuanceId.value = typeof issuance.issuanceId === 'string' ? issuance.issuanceId : ''
    pushSigningEvent('Issuer签发完成', `issuanceId=${issuerIssuanceId.value || '-'}`, 'OK')

    pushSigningEvent('步骤2：Identity写入凭证', '将消费者凭证写入 Identity Hub。', 'RUNNING')
    const credential = await requestJson<Record<string, unknown>>(`${IDENTITY_BASE}/api/identity/credentials`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': consumerId.value
      },
      body: JSON.stringify({
        type: 'MembershipCredential',
        issuer: 'issuer-A',
        claims: { participant: consumerId.value, level: 'STANDARD', scope: 'CATALOG_ACCESS' },
        expiresAt: '2026-12-31T00:00:00Z',
        issuanceId: issuerIssuanceId.value
      })
    })
    signingCredentialId.value = typeof credential.id === 'string' ? credential.id : ''
    if (!signingCredentialId.value) {
      throw new Error('Identity Hub 未返回凭证ID。')
    }
    pushSigningEvent('Identity凭证写入完成', `credentialId=${signingCredentialId.value}`, 'OK')

    pushSigningEvent('步骤3：创建DCP展示', '为供应方生成可验证的展示对象。', 'RUNNING')
    const presentation = await requestJson<Record<string, unknown>>(`${IDENTITY_BASE}/api/dcp/presentations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': consumerId.value
      },
      body: JSON.stringify({
        credentialId: signingCredentialId.value,
        audience: participantId.value
      })
    })
    signingPresentationId.value = typeof presentation.presentationId === 'string' ? presentation.presentationId : ''
    if (!signingPresentationId.value) {
      throw new Error('DCP 展示创建失败，缺少 presentationId。')
    }
    pushSigningEvent('DCP展示创建完成', `presentationId=${signingPresentationId.value}`, 'OK')

    pushSigningEvent('步骤4：校验DCP展示', '验证消费者身份材料有效。', 'RUNNING')
    await requestJson<Record<string, unknown>>(`${IDENTITY_BASE}/api/dcp/verification`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': participantId.value
      },
      body: JSON.stringify({
        presentationId: signingPresentationId.value
      })
    })
    pushSigningEvent('DCP校验通过', '身份材料已验证，允许进入合同协商。', 'OK')

    if (!selectedOfferId.value) {
      useLatestCatalogOffer()
    }
    pushSigningEvent('步骤5：创建合同协商', '基于资产与 Offer 套餐创建 negotiation/agreement。', 'RUNNING')
    await createNegotiation()
    if (!latestNegotiation.value?.agreementId) {
      throw new Error('协商未生成 agreementId，请检查 offer 和资产。')
    }
    pushSigningEvent('合同签约成功', `agreementId=${latestNegotiation.value.agreementId}`, 'OK')
    signingFlowMessage.value = '签发与签约流程已完成，可继续发起传输。'
  } catch (err) {
    const message = normalizeError(err)
    pushSigningEvent('流程失败', message, 'ERROR')
    signingFlowMessage.value = `签发与签约失败：${message}`
  } finally {
    signingFlowRunning.value = false
  }
}

async function loadNegotiations() {
  negotiationsLoading.value = true
  try {
    negotiations.value = await requestJson<NegotiationItem[]>(`${CONTROL_BASE}/api/contracts/negotiations`)
    negotiationPage.value = 1
  } catch (err) {
    negotiationError.value = normalizeError(err)
  } finally {
    negotiationsLoading.value = false
  }
}

async function loadAgreements() {
  agreementsLoading.value = true
  try {
    agreements.value = await requestJson<AgreementItem[]>(`${CONTROL_BASE}/api/contracts/agreements`)
    agreementPage.value = 1
  } catch (err) {
    negotiationError.value = normalizeError(err)
  } finally {
    agreementsLoading.value = false
  }
}

function useAgreement(agreementId: string) {
  selectedAgreementId.value = agreementId
  orchestrationPreview.value = null
  orchestrationPreviewError.value = ''
}

async function previewTransferOrchestration() {
  if (!selectedAgreementId.value) {
    orchestrationPreviewError.value = '请先选择协议 ID。'
    return
  }

  orchestrationPreviewLoading.value = true
  orchestrationPreviewError.value = ''
  orchestrationPreview.value = null

  try {
    const agreementParam = encodeURIComponent(selectedAgreementId.value)
    const dataPlaneQuery = selectedDataPlaneId.value ? `&dataPlaneId=${encodeURIComponent(selectedDataPlaneId.value)}` : ''
    const response = await requestJson<OrchestrationPreview>(
      `${CONTROL_BASE}/api/transfers/orchestration/preview?agreementId=${agreementParam}${dataPlaneQuery}`,
      {
        headers: {
          'X-Participant-Id': orchestrationParticipantId.value,
          'X-Operator-Token': orchestrationToken.value
        }
      }
    )
    orchestrationPreview.value = response
    transferGovernanceMessage.value = response.readyToTransfer
      ? '编排预演通过：会员/签发资格/计费/数据面均可执行。'
      : '编排预演未通过：请先修复失败步骤。'
  } catch (err) {
    const code = extractHttpStatus(err)
    if (code === 403) {
      orchestrationPreviewError.value = '权限校验失败：请使用 operator 参与方与正确的 X-Operator-Token。'
    } else {
      orchestrationPreviewError.value = normalizeError(err)
    }
  } finally {
    orchestrationPreviewLoading.value = false
  }
}

async function startTransfer() {
  if (!selectedAgreementId.value) {
    transferError.value = '请先选择或填写协议 ID。'
    return
  }

  const transferParticipant = resolveTransferConsumerId()
  const selectedAgreement = agreements.value.find((item) => item.agreementId === selectedAgreementId.value)
  const transferAudience = selectedAgreement?.providerId || participantId.value
  startTransferLoading.value = true
  transferError.value = ''
  transferMembership.value = null
  transferUsage.value = null
  transferQualification.value = null
  transferGovernanceMessage.value = ''

  try {
    const activeMembership = await queryActiveMembership(transferParticipant)
    transferMembership.value = activeMembership
    transferGovernanceMessage.value = `会员校验通过：${activeMembership.id}`

    const qualification = await queryQualificationStatus(transferParticipant, transferAudience)
    transferQualification.value = qualification
    if (!qualification?.qualified) {
      const reason = qualification?.reason || 'UNKNOWN'
      throw new Error(`HTTP 403: Credential qualification required, reason=${reason}`)
    }
    transferGovernanceMessage.value = `${transferGovernanceMessage.value}；签发资格校验通过。`

    const response = await requestJson<TransferProcessResponse>(`${CONTROL_BASE}/api/transfers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agreementId: selectedAgreementId.value,
        protocol: 'DSP',
        dataPlaneId: selectedDataPlaneId.value || null
      })
    })

    selectedTransferId.value = response.id
    transferGovernanceMessage.value = `${transferGovernanceMessage.value}；计费扣减已执行。`
    await Promise.all([loadTransferStatuses(), inspectTransfer(response.id)])
  } catch (err) {
    transferError.value = governanceFailureMessage('传输', err)
    if (!transferGovernanceMessage.value) {
      transferGovernanceMessage.value = transferError.value
    }
  } finally {
    transferUsage.value = await queryUsageStatus(transferParticipant, transferUsageCode())
    if (transferUsage.value) {
      transferGovernanceMessage.value = `${transferGovernanceMessage.value || '治理检查完成'}；计费剩余 ${transferUsage.value.remainingCount}`
    }
    startTransferLoading.value = false
  }
}

async function loadTransferStatuses() {
  transferLoading.value = true
  try {
    transferStatuses.value = await requestJson<TransferStatus[]>(`${CONTROL_BASE}/api/transfers/status`)
    transferPage.value = 1
    if (!selectedTransferId.value && transferStatuses.value.length > 0) {
      selectedTransferId.value = transferStatuses.value[0].transferProcessId
    }
  } catch (err) {
    transferError.value = normalizeError(err)
  } finally {
    transferLoading.value = false
  }
}

async function inspectTransfer(transferProcessId: string) {
  selectedTransferId.value = transferProcessId
  transferError.value = ''

  try {
    transferTrace.value = await requestJson<TransferTrace>(`${CONTROL_BASE}/api/transfers/${transferProcessId}/trace`)
    currentEdr.value = await requestJson<TransferEdr>(`${CONTROL_BASE}/api/transfers/${transferProcessId}/edr`)
  } catch (err) {
    transferError.value = normalizeError(err)
    transferTrace.value = null
    currentEdr.value = null
  }
}

async function pullTransferData() {
  if (!currentEdr.value) {
    transferError.value = '请先查询到有效 EDR。'
    return
  }

  pullingData.value = true
  transferError.value = ''

  try {
    const data = await requestJson<unknown>(
      `${currentEdr.value.endpoint}?message=${encodeURIComponent(pullMessage.value)}`,
      {
        headers: { Authorization: currentEdr.value.authToken }
      }
    )
    pulledPayload.value = prettyJson(data)
  } catch (err) {
    transferError.value = normalizeError(err)
  } finally {
    pullingData.value = false
  }
}

async function runDualPlaneDemo() {
  dualDemoRunning.value = true
  dualError.value = ''

  try {
    const response = await requestJson<DualPlaneDemoResult>(`${CONTROL_BASE}/api/scenario/dual-plane-demo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ consumerId: consumerId.value })
    })

    dualDemoResult.value = response
    dualPayloads.value = {}
    pushTimeline('双平面流程已创建', `runId=${response.runId}`, 'OK')

    await Promise.all([loadCatalog(), loadNegotiations(), loadAgreements(), loadTransferStatuses()])
    if (response.steps.length > 0) {
      await inspectTransfer(response.steps[0].transferProcessId)
    }
  } catch (err) {
    dualError.value = normalizeError(err)
  } finally {
    dualDemoRunning.value = false
  }
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

async function playDualPlaneDemo() {
  if (!dualDemoResult.value || dualDemoResult.value.steps.length === 0) {
    dualError.value = '请先执行“生成双平面流程”。'
    return
  }

  dualPlayRunning.value = true
  dualError.value = ''

  try {
    for (const step of dualDemoResult.value.steps) {
      pushTimeline(`${step.dataPlaneId} 开始`, `传输 ${step.transferProcessId} 进入链路追踪`, 'RUNNING')
      try {
        const trace = await requestJson<TransferTrace>(`${CONTROL_BASE}/api/transfers/${step.transferProcessId}/trace`)
        const edr = await requestJson<TransferEdr>(`${CONTROL_BASE}/api/transfers/${step.transferProcessId}/edr`)
        const payload = await requestJson<unknown>(
          `${edr.endpoint}?message=${encodeURIComponent(`dual-${step.dataPlaneId}`)}`,
          {
            headers: { Authorization: edr.authToken }
          }
        )

        dualPayloads.value = {
          ...dualPayloads.value,
          [step.transferProcessId]: prettyJson(payload)
        }

        const controlState = trace.transfer?.controlState ?? 'UNKNOWN'
        const dataPlaneState = trace.transfer?.dataPlaneState ?? 'UNKNOWN'
        pushTimeline(
          `${step.dataPlaneId} 完成`,
          `${step.transferProcessId} => control=${controlState}, dataPlane=${dataPlaneState}`,
          'OK'
        )
      } catch (err) {
        pushTimeline(`${step.dataPlaneId} 失败`, normalizeError(err), 'ERROR')
      }

      await sleep(260)
    }

    await loadTransferStatuses()
  } finally {
    dualPlayRunning.value = false
  }
}

async function ensureCatalogSelection() {
  if (catalogItems.value.length === 0) {
    await loadCatalog()
  }
  if (!selectedAssetId.value && sortedCatalogItems.value.length > 0) {
    selectedAssetId.value = sortedCatalogItems.value[0].asset.id
    await loadAssetDetail(selectedAssetId.value)
  }
  if (selectedAssetId.value && !selectedOfferId.value) {
    await loadAssetDetail(selectedAssetId.value)
    useLatestCatalogOffer()
  }
}

async function runBillingUsageCheck() {
  const result = await requestJson<unknown>(`${OPERATOR_BASE}/api/billing/usage/check`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      participantId: participantId.value,
      serviceCode: 'FEDERATED_CATALOG_QUERY'
    })
  })
  guideBillingOutput.value = prettyJson(result)
}

async function executeCurrentGuideStep() {
  guideRunning.value = true
  guideMessage.value = ''

  try {
    const stepId = currentGuideStep.value.id
    if (stepId === 'guide-1') {
      await Promise.all([loadIdentity(), loadCatalog()])
      guideMessage.value = '已完成身份与目录加载：现在可以开始选资产。'
    } else if (stepId === 'guide-2') {
      await ensureCatalogSelection()
      if (!selectedAssetId.value) {
        throw new Error('目录为空，请先生成演示数据。')
      }
      await runConsumerSigningFlow()
      if (!latestNegotiation.value?.agreementId) {
        throw new Error('签发或协商未成功，请查看“签发与签约流程”详情。')
      }
      guideMessage.value = '签发与协商已完成，已产出 negotiationId 与 agreementId。'
    } else if (stepId === 'guide-3') {
      await Promise.all([loadNegotiations(), loadAgreements()])
      if (agreements.value.length > 0 && !selectedAgreementId.value) {
        selectedAgreementId.value = agreements.value[0].agreementId
      }
      guideMessage.value = '协商列表与协议列表已刷新。'
    } else if (stepId === 'guide-4') {
      if (!selectedAgreementId.value) {
        await loadAgreements()
        if (agreements.value.length > 0) {
          selectedAgreementId.value = agreements.value[0].agreementId
        }
      }
      await previewTransferOrchestration()
      if (!orchestrationPreview.value?.readyToTransfer) {
        throw new Error(orchestrationPreview.value?.message || orchestrationPreviewError.value || '编排预演未通过。')
      }
      const beforeTransferId = selectedTransferId.value
      await startTransfer()
      if (selectedTransferId.value === beforeTransferId) {
        throw new Error('传输未成功发起，请确认协议 ID 是否有效。')
      }
      guideMessage.value = '传输已发起，链路追踪已更新。'
    } else if (stepId === 'guide-5') {
      if (!currentEdr.value && selectedTransferId.value) {
        await inspectTransfer(selectedTransferId.value)
      }
      pulledPayload.value = ''
      await pullTransferData()
      if (!pulledPayload.value) {
        throw new Error('尚未获取到传输数据。')
      }
      guideMessage.value = 'EDR 拉取成功，可直接展示 payload。'
    } else if (stepId === 'guide-6') {
      await runDualPlaneDemo()
      guideMessage.value = '双平面流程已生成，包含 dp-1 与 dp-2。'
    } else if (stepId === 'guide-7') {
      await runBillingUsageCheck()
      guideMessage.value = '计费校验完成，已显示使用次数与剩余额度。'
    }
  } catch (err) {
    guideMessage.value = `步骤执行失败：${normalizeError(err)}`
  } finally {
    guideRunning.value = false
  }
}

async function runGuideFromStart() {
  guideRunning.value = true
  guideMessage.value = '开始执行 7 步演示流程...'
  guideStepIndex.value = 0
  guideBillingOutput.value = ''

  try {
    for (let i = 0; i < guideSteps.length; i += 1) {
      guideStepIndex.value = i
      await executeCurrentGuideStep()
      if (guideMessage.value.startsWith('步骤执行失败')) {
        break
      }
      await sleep(220)
    }
    if (!guideMessage.value.startsWith('步骤执行失败')) {
      guideMessage.value = '7 步演示已完成，可按当前高亮区域逐段讲解。'
    }
  } finally {
    guideRunning.value = false
  }
}

onMounted(async () => {
  const tasks: Array<Promise<unknown>> = [loadIdentity(), loadCatalog(), loadNegotiations(), loadAgreements(), loadTransferStatuses()]
  if (isProvider.value) {
    tasks.push(loadPolicies())
  }
  await Promise.all(tasks)
})
</script>

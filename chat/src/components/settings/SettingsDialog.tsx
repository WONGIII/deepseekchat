import clsx from 'clsx'
import { useRef, useState, type ReactNode } from 'react'
import {
  IconDarkOutline16,
  IconDataOutline16,
  IconDownloadOutline16,
  IconFollowsystemOutline16,
  IconLightOutline16,
  IconLinkOutline16,
  IconSettingsOutline16,
  IconThinkOutline16,
  IconUploadOutline14,
} from '../base/icons.js'
import {
  BackupError,
  clearHistory,
  exportHistory,
  importHistory,
} from '../../services/chat/backup.js'
import { useChatStore } from '../../services/chat/store.js'
import { regionCodes, getRegionName, provinces } from '../../services/location.js'
import {
  DEFAULT_LLM_BASE_URL,
  DEFAULT_SEARCH_BASE_URL,
  DEFAULT_SPEECH_BASE_URL,
} from '../../services/api/endpoints.js'
import { ApiError } from '../../services/api/client.js'
import { classifyApiError } from '../../services/api/errors.js'
import { fetchModels } from '../../services/api/models.js'
import type { TtsEmotion } from '../../services/api/speech.js'
import {
  addModel,
  getLlmApiKey,
  isChineseBrowser,
  normalizeBaseUrl,
  setLlmApiKey,
  setLlmBaseUrl,
  setLocale,
  setLocation,
  setModel,
  setSearchApiKey,
  setSearchBaseUrl,
  setSpeechApiKey,
  setSpeechBaseUrl,
  setThemePreference,
  setTtsEmotion,
  setTtsVoice,
  useSettings,
  type ThemePreference,
} from '../../services/settings.js'
import { useTranslate, type TranslationKey, type TranslationMessage } from '../../i18n/index.js'
import { Button } from '../base/Button.js'
import { Modal } from '../base/Modal.js'
import { Select } from '../base/Select.js'
import { showToast } from '../base/toastState.js'
import { closeSettings, useSettingsDialog, type SettingsSection } from './settingsDialogState.js'
import styles from './Settings.module.css'

/**
 * 设置弹窗：分「通用设置」（主题、语言、朗读音色、位置）、「模型设置」（当前模型、获取与添加自定义模型）、
 * 「网络设置」（LLM、搜索、语音的地址与 API Key）与「数据管理」（导入、导出、删除）。
 * 桌面端为居中弹窗、左侧导航；移动端为底部弹层、导航横排在内容上方
 */
export const SettingsDialog = () => {
  const open = useSettingsDialog((state) => state.open)
  return open ? <SettingsContent /> : null
}

const sections: { value: SettingsSection; label: TranslationKey; icon: ReactNode }[] = [
  {
    value: 'general',
    label: 'settingDialogAsideGeneralOptionLabel',
    icon: <IconSettingsOutline16 />,
  },
  { value: 'model', label: 'settingDialogAsideModelOptionLabel', icon: <IconThinkOutline16 /> },
  { value: 'network', label: 'settingDialogAsideNetworkOptionLabel', icon: <IconLinkOutline16 /> },
  { value: 'data', label: 'settingDialogAsideDataOptionLabel', icon: <IconDataOutline16 /> },
]

const SettingsContent = () => {
  const t = useTranslate()
  // 打开时按调用方指定的分组选中，之后在弹窗内切换
  const [section, setSection] = useState(() => useSettingsDialog.getState().section)
  // 切换分组不丢失导入状态
  const historyImport = useHistoryImport()
  const historyDelete = useHistoryDelete()
  const replacingHistory = useChatStore((state) => state.importingHistory)
  const historyBusy = historyImport.busy || historyDelete.busy || replacingHistory
  return (
    <>
      <Modal
        title={t('settings')}
        closable
        className={styles.dialog}
        mainClassName={styles.dialogMain}
        onClose={() => {
          if (!historyDelete.busy) {
            closeSettings()
          }
        }}
      >
        <div role="tablist" className={styles.navigation}>
          {sections.map((item) => (
            <button
              key={item.value}
              type="button"
              role="tab"
              aria-selected={section === item.value}
              className={clsx(styles.navItem, section === item.value && styles.navItemActive)}
              onClick={() => setSection(item.value)}
            >
              {item.icon}
              {t(item.label)}
            </button>
          ))}
        </div>
        <div role="tabpanel" className={styles.content}>
          {section === 'general' ? (
            <>
              <ThemeSetting />
              <LocaleSetting />
              <TtsVoiceSetting />
              <TtsEmotionSetting />
              <LocationSetting />
            </>
          ) : section === 'model' ? (
            <ModelSettings />
          ) : section === 'network' ? (
            <NetworkSettings />
          ) : (
            <>
              <ImportHistory historyImport={historyImport} disabled={historyBusy} />
              <ExportHistory disabled={historyBusy} />
              <SettingCell
                label={t('deleteAllSessions')}
                status={null}
                action={
                  <Button
                    variant="dangerOutlined"
                    size="m"
                    className={styles.cellButton}
                    disabled={historyBusy}
                    onClick={historyDelete.open}
                  >
                    {t('delete')}
                  </Button>
                }
              />
            </>
          )}
        </div>
      </Modal>
      {historyImport.file ? <ImportConfirm historyImport={historyImport} /> : null}
      {historyDelete.opened ? <DeleteAllConfirm historyDelete={historyDelete} /> : null}
    </>
  )
}

/** 删除确认保留在设置之上；失败可重试，成功只关闭确认弹窗。 */
const DeleteAllConfirm = ({
  historyDelete,
}: {
  historyDelete: ReturnType<typeof useHistoryDelete>
}) => {
  const t = useTranslate()
  return (
    <Modal
      title={t('deleteAllSessionsModalTitle')}
      className={styles.deleteConfirm}
      onClose={historyDelete.cancel}
      footer={
        <div className={styles.deleteActions} aria-busy={historyDelete.busy}>
          <Button
            variant="outlined"
            autoFocus
            disabled={historyDelete.busy}
            onClick={historyDelete.cancel}
          >
            {t('cancel')}
          </Button>
          <Button
            variant="danger"
            disabled={historyDelete.busy}
            onClick={() => void historyDelete.confirm()}
          >
            {t(historyDelete.busy ? 'historyDeleting' : 'deleteAllSessions')}
          </Button>
        </div>
      }
    >
      {t('deleteAllSessionsModalDescription')}
    </Modal>
  )
}

const useHistoryDelete = () => {
  const [opened, setOpened] = useState(false)
  const [busy, setBusy] = useState(false)
  const deleting = useRef(false)

  const confirm = async () => {
    if (deleting.current) {
      return
    }
    deleting.current = true
    setBusy(true)
    try {
      await clearHistory()
      setOpened(false)
      showToast({ key: 'historyDeleted' })
    } catch {
      showToast({ key: 'historyDeleteFailed', type: 'error' })
    } finally {
      deleting.current = false
      setBusy(false)
    }
  }

  return {
    opened,
    busy,
    open: () => setOpened(true),
    cancel: () => {
      if (!deleting.current) {
        setOpened(false)
      }
    },
    confirm,
  }
}

/** 模型设置：当前模型、从 LLM 地址同源获取模型、手动添加自定义模型 */
const ModelSettings = () => {
  const t = useTranslate()
  const model = useSettings((state) => state.model)
  const models = useSettings((state) => state.models)
  const llmApiKey = useSettings((state) => state.llmApiKey)
  const llmBaseUrl = useSettings((state) => state.llmBaseUrl)
  const primaryModelsUrl = `${llmBaseUrl ?? DEFAULT_LLM_BASE_URL}/models`
  const [custom, setCustom] = useState('')
  const [fetching, setFetching] = useState(false)
  const [fetched, setFetched] = useState<string[] | null>(null)
  const [status, setStatus] = useState<Status>(null)
  const options = (models.includes(model) ? models : [...models, model]).map((id) => ({
    value: id,
    label: id,
  }))

  const fetchModelList = async () => {
    if (fetching || !llmApiKey) {
      return
    }
    setFetching(true)
    setFetched(null)
    setStatus(null)
    try {
      const ids = await fetchModels(getLlmApiKey())
      setFetched(ids)
      if (ids.length === 0) {
        setStatus({ key: 'settingsModelsEmpty' })
      }
    } catch (error) {
      setStatus(describeModelError(error))
    } finally {
      setFetching(false)
    }
  }

  return (
    <>
      <SettingCell
        label={t('settingsModel')}
        description={t('settingsModelDescription')}
        status={null}
        action={
          <Select
            ariaLabel={t('settingsModel')}
            value={model}
            options={options}
            onChange={setModel}
          />
        }
      />
      <SettingCell
        label={t('settingsModelFetch')}
        description={
          llmApiKey
            ? t('settingsModelFetchDescription', { url: primaryModelsUrl })
            : t('settingsModelsNeedApiKey')
        }
        status={status}
        action={
          <Button
            variant="outlined"
            size="m"
            className={styles.cellButton}
            disabled={!llmApiKey || fetching}
            onClick={() => void fetchModelList()}
          >
            {t(fetching ? 'settingsModelsFetching' : 'settingsModelFetch')}
          </Button>
        }
      />
      {fetched && fetched.length > 0 ? (
        <div className={styles.modelList}>
          {fetched.map((id) => (
            <div key={id} className={styles.modelItem}>
              <span className={styles.modelId}>{id}</span>
              {models.includes(id) ? (
                <span className={styles.modelAdded}>{t('settingsModelAdded')}</span>
              ) : (
                <button type="button" className={styles.modelAdd} onClick={() => addModel(id)}>
                  {t('settingsModelAdd')}
                </button>
              )}
            </div>
          ))}
        </div>
      ) : null}
      <SettingCell
        label={t('settingsCustomModel')}
        description={t('settingsCustomModelDescription')}
        status={null}
        className={styles.urlCell}
        action={
          <div className={styles.urlInput}>
            <input
              type="text"
              autoComplete="off"
              spellCheck={false}
              aria-label={t('settingsCustomModel')}
              placeholder={t('settingsCustomModelPlaceholder')}
              value={custom}
              onChange={(event) => setCustom(event.target.value)}
              onKeyDown={(event) => {
                if (
                  event.key === 'Enter' &&
                  !event.nativeEvent.isComposing &&
                  custom.trim()
                ) {
                  addModel(custom)
                  setCustom('')
                }
              }}
            />
            <button
              type="button"
              className={styles.urlReset}
              disabled={!custom.trim()}
              onClick={() => {
                addModel(custom)
                setCustom('')
              }}
            >
              {t('settingsModelAdd')}
            </button>
          </div>
        }
      />
    </>
  )
}

/** 网络设置：三类请求各自使用完整路径前缀与 API Key，地址留空回退默认值 */
const NetworkSettings = () => {
  const t = useTranslate()
  const llmBaseUrl = useSettings((state) => state.llmBaseUrl)
  const searchBaseUrl = useSettings((state) => state.searchBaseUrl)
  const speechBaseUrl = useSettings((state) => state.speechBaseUrl)
  const llmApiKey = useSettings((state) => state.llmApiKey)
  const searchApiKey = useSettings((state) => state.searchApiKey)
  const speechApiKey = useSettings((state) => state.speechApiKey)
  return (
    <>
      <div className={styles.networkHint}>{t('settingsNetworkDescription')}</div>
      <BaseUrlSetting
        label={t('settingsLlmBaseUrl')}
        description={t('settingsLlmBaseUrlDescription', { url: DEFAULT_LLM_BASE_URL })}
        defaultValue={DEFAULT_LLM_BASE_URL}
        value={llmBaseUrl}
        onChange={setLlmBaseUrl}
      />
      <SecretSetting
        label={t('settingsLlmApiKey')}
        description={t('settingsLlmApiKeyDescription')}
        placeholder={t('apiKeyPlaceholder')}
        value={llmApiKey}
        onChange={setLlmApiKey}
      />
      <BaseUrlSetting
        label={t('settingsSearchBaseUrl')}
        description={t('settingsSearchBaseUrlDescription', { url: DEFAULT_SEARCH_BASE_URL })}
        defaultValue={DEFAULT_SEARCH_BASE_URL}
        value={searchBaseUrl}
        onChange={setSearchBaseUrl}
      />
      <SecretSetting
        label={t('settingsSearchApiKey')}
        description={t('settingsApiKeyFallbackDescription')}
        placeholder={t('settingsApiKeyFallbackPlaceholder')}
        value={searchApiKey}
        onChange={setSearchApiKey}
      />
      <BaseUrlSetting
        label={t('settingsSpeechBaseUrl')}
        description={t('settingsSpeechBaseUrlDescription', { url: DEFAULT_SPEECH_BASE_URL })}
        defaultValue={DEFAULT_SPEECH_BASE_URL}
        value={speechBaseUrl}
        onChange={setSpeechBaseUrl}
      />
      <SecretSetting
        label={t('settingsSpeechApiKey')}
        description={t('settingsApiKeyFallbackDescription')}
        placeholder={t('settingsApiKeyFallbackPlaceholder')}
        value={speechApiKey}
        onChange={setSpeechApiKey}
      />
    </>
  )
}

/**
 * 接口地址输入行：失焦或回车时校验并保存；非法输入显示错误且不写入设置。
 * 有自定义值时提供「恢复默认」，清空输入同样回退默认。
 */
const BaseUrlSetting = ({
  label,
  description,
  defaultValue,
  value,
  onChange,
}: {
  label: string
  description: string
  defaultValue: string
  value: string | null
  onChange: (value: string | null) => void
}) => {
  const t = useTranslate()
  const [draft, setDraft] = useState(value ?? '')
  const [invalid, setInvalid] = useState(false)
  const commit = () => {
    if (!draft.trim()) {
      setInvalid(false)
      onChange(null)
      return
    }
    const normalized = normalizeBaseUrl(draft)
    if (!normalized) {
      setInvalid(true)
      return
    }
    setDraft(normalized)
    setInvalid(false)
    onChange(normalized)
  }
  return (
    <SettingCell
      label={label}
      description={description}
      status={invalid ? { key: 'settingsBaseUrlInvalid', error: true } : null}
      className={styles.urlCell}
      action={
        <div className={clsx(styles.urlInput, invalid && styles.urlInputInvalid)}>
          <input
            type="text"
            inputMode="url"
            autoComplete="off"
            spellCheck={false}
            aria-label={label}
            placeholder={defaultValue}
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value)
              if (invalid) {
                setInvalid(false)
              }
            }}
            onBlur={commit}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
                event.currentTarget.blur()
              }
            }}
          />
          {value ? (
            <button
              type="button"
              className={styles.urlReset}
              // 保持输入框焦点，避免触发额外的失焦提交
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => {
                setDraft('')
                setInvalid(false)
                onChange(null)
              }}
            >
              {t('settingsBaseUrlReset')}
            </button>
          ) : null}
        </div>
      }
    />
  )
}

/**
 * API Key 输入行：失焦或回车直接保存，不做网络校验；有值时提供「清除」。
 * 搜索/语音留空即沿用 LLM Key。
 */
const SecretSetting = ({
  label,
  description,
  placeholder,
  value,
  onChange,
}: {
  label: string
  description: string
  placeholder: string
  value: string | null
  onChange: (value: string | null) => void
}) => {
  const t = useTranslate()
  const [draft, setDraft] = useState(value ?? '')
  const commit = () => {
    const next = draft.trim() || null
    setDraft(next ?? '')
    onChange(next)
  }
  return (
    <SettingCell
      label={label}
      description={description}
      status={null}
      className={styles.urlCell}
      action={
        <div className={styles.urlInput}>
          <input
            type="password"
            autoComplete="off"
            spellCheck={false}
            aria-label={label}
            placeholder={placeholder}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onBlur={commit}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
                event.currentTarget.blur()
              }
            }}
          />
          {value ? (
            <button
              type="button"
              className={styles.urlReset}
              // 保持输入框焦点，避免触发额外的失焦提交
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => {
                setDraft('')
                onChange(null)
              }}
            >
              {t('settingsApiKeyClear')}
            </button>
          ) : null}
        </div>
      }
    />
  )
}

const LocationSetting = () => {
  const t = useTranslate()
  const locale = useSettings((state) => state.locale)
  const location = useSettings((state) => state.location)
  const province =
    location?.region === 'CN'
      ? provinces.find((item) => item.code === location.province)
      : undefined

  return (
    <SettingCell
      label={t('settingsLocation')}
      status={null}
      className={styles.locationCell}
      action={
        <div className={styles.locationSelectors}>
          {isChineseBrowser() ? (
            <>
              <Select
                ariaLabel={t('settingsLocationProvince')}
                className={styles.provinceSelect}
                value={province?.code ?? ''}
                placeholder={t('settingsLocationProvince')}
                options={provinces.map((item) => ({ value: item.code, label: item.name }))}
                onChange={(value) => {
                  if (value !== province?.code) {
                    setLocation({ region: 'CN', province: value })
                  }
                }}
              />
              <Select
                ariaLabel={t('settingsLocationCity')}
                className={styles.citySelect}
                value={province ? (location?.city ?? '') : ''}
                disabled={!province?.cities.length}
                placeholder={t('settingsLocationCity')}
                options={
                  province?.cities.map((item) => ({ value: item.code, label: item.name })) ?? []
                }
                onChange={(value) => {
                  if (province) {
                    setLocation({ region: 'CN', province: province.code, city: value })
                  }
                }}
              />
            </>
          ) : (
            <Select
              ariaLabel={t('settingsLocationRegion')}
              className={styles.regionSelect}
              value={location?.region ?? ''}
              placeholder={t('settingsLocationRegion')}
              options={regionCodes
                .map((code) => ({ value: code, label: getRegionName(code, locale) }))
                .sort((a, b) => a.label.localeCompare(b.label, locale))}
              onChange={(value) => setLocation({ region: value })}
            />
          )}
        </div>
      }
    />
  )
}

const LocaleSetting = () => {
  const t = useTranslate()
  const locale = useSettings((state) => state.locale)
  return (
    <SettingCell
      label={t('settingsLocalePreference')}
      status={null}
      action={
        <Select
          ariaLabel={t('settingsLocalePreference')}
          value={locale}
          options={[
            { value: 'zh-CN', label: t('settingsLocaleZhCN'), lang: 'zh-CN' },
            { value: 'en-US', label: t('settingsLocaleEnUS'), lang: 'en' },
          ]}
          onChange={setLocale}
        />
      }
    />
  )
}

/** 朗读音色：stella（海星，女声）/ tide（暗潮，男声） */
const TtsVoiceSetting = () => {
  const t = useTranslate()
  const ttsVoice = useSettings((state) => state.ttsVoice)
  return (
    <SettingCell
      label={t('settingsTtsVoice')}
      status={null}
      action={
        <Select
          ariaLabel={t('settingsTtsVoice')}
          value={ttsVoice}
          options={[
            { value: 'stella', label: t('settingsTtsVoiceStella') },
            { value: 'tide', label: t('settingsTtsVoiceTide') },
          ]}
          onChange={setTtsVoice}
        />
      }
    />
  )
}

/** 朗读语气：auto 跟随参考音频，其余由 CosyVoice instruct 指令控制 */
const ttsEmotionOptions: { value: TtsEmotion; label: TranslationKey }[] = [
  { value: 'auto', label: 'settingsTtsEmotionAuto' },
  { value: 'slow', label: 'settingsTtsEmotionSlow' },
  { value: 'gentle', label: 'settingsTtsEmotionGentle' },
  { value: 'happy', label: 'settingsTtsEmotionHappy' },
  { value: 'sad', label: 'settingsTtsEmotionSad' },
  { value: 'serious', label: 'settingsTtsEmotionSerious' },
  { value: 'excited', label: 'settingsTtsEmotionExcited' },
  { value: 'dynamic', label: 'settingsTtsEmotionDynamic' },
]

const TtsEmotionSetting = () => {
  const t = useTranslate()
  const ttsEmotion = useSettings((state) => state.ttsEmotion)
  return (
    <SettingCell
      label={t('settingsTtsEmotion')}
      status={null}
      action={
        <Select
          ariaLabel={t('settingsTtsEmotion')}
          value={ttsEmotion}
          options={ttsEmotionOptions.map((option) => ({
            value: option.value,
            label: t(option.label),
          }))}
          onChange={setTtsEmotion}
        />
      }
    />
  )
}

/** 覆盖导入的二次确认，叠在设置弹窗上 */
const ImportConfirm = ({
  historyImport,
}: {
  historyImport: ReturnType<typeof useHistoryImport>
}) => {
  const t = useTranslate()
  return (
    <Modal
      title={t('historyImportConfirmTitle')}
      className={styles.importConfirm}
      onClose={historyImport.cancel}
      footer={
        <>
          <Button variant="outlined" size="m" autoFocus onClick={historyImport.cancel}>
            {t('cancel')}
          </Button>
          <Button variant="dangerOutlined" size="m" onClick={() => void historyImport.confirm()}>
            {t('historyImportConfirm')}
          </Button>
        </>
      }
    >
      <div className={styles.importConfirmText}>{t('historyImportConfirmDescription')}</div>
    </Modal>
  )
}

const themeOptions: { value: ThemePreference; label: TranslationKey; icon: ReactNode }[] = [
  {
    value: 'light',
    label: 'avatarMenuSettingsDialogThemeSelectLightOption',
    icon: <IconLightOutline16 />,
  },
  {
    value: 'dark',
    label: 'avatarMenuSettingsDialogThemeSelectDarkOption',
    icon: <IconDarkOutline16 />,
  },
  {
    value: 'system',
    label: 'avatarMenuSettingsDialogThemeSelectSystemOption',
    icon: <IconFollowsystemOutline16 />,
  },
]

/**
 * 主题项：标题下方浅色 / 深色 / 跟随系统三个选块。
 * 与下方语言选项连续排列。
 */
const ThemeSetting = () => {
  const t = useTranslate()
  const themePreference = useSettings((state) => state.themePreference)
  const label = t('avatarMenuSettingsDialogThemeItemLabel')
  return (
    <div className={styles.theme}>
      {label}
      <div role="radiogroup" aria-label={label} className={styles.themeOptions}>
        {themeOptions.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={themePreference === option.value}
            className={clsx(
              styles.themeOption,
              themePreference === option.value && styles.themeOptionSelected
            )}
            onClick={() => setThemePreference(option.value)}
          >
            {option.icon}
            {t(option.label)}
          </button>
        ))}
      </div>
    </div>
  )
}

/** `reason` 为失败原因的文案 key，展示时按当前语言翻译后填入 `message` 参数。 */
type Status = (TranslationMessage & { error?: boolean; reason?: TranslationKey }) | null

/** 导出失败附带原因：预期的错误用对应文案作原因，其他错误用错误信息。 */
const errorStatus = (key: TranslationKey, error: unknown): Status => ({
  key,
  ...(error instanceof BackupError
    ? { reason: error.key }
    : { params: { message: error instanceof Error ? error.message : String(error) } }),
  error: true,
})

/** 获取模型失败：无效 Key、接口返回非模型列表、网络错误与其他错误分别提示 */
const describeModelError = (error: unknown): Status => {
  const kind = classifyApiError(error)
  if (kind === 'unauthorized') {
    return { key: 'apiKeyInvalid', error: true }
  }
  if (kind === 'network') {
    return { key: 'commonNetworkErrorToast', error: true }
  }
  if (error instanceof ApiError && error.kind === 'protocol') {
    return { key: 'settingsModelsInvalidResponse', error: true }
  }
  return {
    key: 'settingsModelsFetchFailed',
    params: { message: error instanceof Error ? error.message : String(error) },
    error: true,
  }
}

const SettingCell = ({
  label,
  description,
  status,
  action,
  className,
}: {
  label: string
  description?: string
  status: Status
  action: ReactNode
  className?: string
}) => {
  const t = useTranslate()
  return (
    <div className={clsx(styles.cell, styles.row, className)}>
      <div className={styles.text}>
        <div className={styles.label}>{label}</div>
        {status || description ? (
          <div className={clsx(styles.description, status?.error && styles.error)}>
            {status
              ? t(status.key, status.reason ? { message: t(status.reason) } : status.params)
              : description}
          </div>
        ) : null}
      </div>
      {action}
    </div>
  )
}

/** 选文件后先提示覆盖；确认后才检查文件并执行导入，任何错误都提示导入失败。 */
const useHistoryImport = () => {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<Status>(null)
  const importing = useRef(false)

  const confirm = async () => {
    if (!file || importing.current) {
      return
    }
    importing.current = true
    setFile(null)
    setBusy(true)
    setStatus(null)
    try {
      const count = await importHistory(file)
      setStatus({ key: count === 1 ? 'historyImportedOne' : 'historyImported', params: { count } })
    } catch {
      setStatus({ key: 'historyImportFailed', error: true })
    } finally {
      importing.current = false
      setBusy(false)
    }
  }

  return { file, busy, status, select: setFile, cancel: () => setFile(null), confirm }
}

/** 导入 = 覆盖：用选中文件替换本地全部会话与附件。 */
const ImportHistory = ({
  historyImport,
  disabled,
}: {
  historyImport: ReturnType<typeof useHistoryImport>
  disabled: boolean
}) => {
  const t = useTranslate()
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <SettingCell
      label={t('historyImport')}
      description={t('historyImportDescription')}
      status={historyImport.status}
      action={
        <>
          <Button
            variant="outlined"
            size="m"
            className={clsx(styles.cellButton, styles.uploadButton)}
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
          >
            <IconUploadOutline14 />
            {t('upload')}
          </Button>
          <input
            ref={inputRef}
            type="file"
            accept=".zip,application/zip,application/x-zip-compressed"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (file) {
                historyImport.select(file)
              }
            }}
          />
        </>
      }
    />
  )
}

/** 将全部会话及图片导出为 ZIP 文件并下载 */
const ExportHistory = ({ disabled }: { disabled: boolean }) => {
  const t = useTranslate()
  const empty = useChatStore((state) => state.sessions.length === 0)
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<Status>(null)

  const onExport = async () => {
    setBusy(true)
    setStatus(null)
    try {
      const { name, blob } = await exportHistory()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = name
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 10_000)
    } catch (error) {
      setStatus(errorStatus('historyExportFailed', error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <SettingCell
      label={t('exportAllLabel')}
      description={t('historyExportDescription')}
      status={status}
      action={
        <Button
          variant="outlined"
          size="m"
          className={styles.cellButton}
          disabled={disabled || busy || empty}
          onClick={() => void onExport()}
        >
          <IconDownloadOutline16 />
          {t('exportAllDownload')}
        </Button>
      }
    />
  )
}

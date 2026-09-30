import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * 蓝色主导的现代管理端主题令牌。
 *
 * 依据用户确认的风格提案（frontend/admin/design-showcase.html 第 01 套）：
 * 浅灰蓝底、浅蓝导航、明亮蓝色主操作、克制的圆角与阴影，不使用渐变背景。
 * 只在这里维护颜色与尺寸，页面不写死色值。
 */
export const calmTechTheme: GlobalThemeOverrides = {
  common: {
    fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
    primaryColor: '#2563eb',
    primaryColorHover: '#3b78f2',
    primaryColorPressed: '#1d4ed8',
    primaryColorSuppl: '#3b78f2',
    infoColor: '#2563eb',
    successColor: '#43aa7a',
    warningColor: '#f6a94a',
    errorColor: '#e2554a',
    textColorBase: '#202938',
    textColor1: '#17212b',
    textColor2: '#3f4b56',
    textColor3: '#8290a2',
    bodyColor: '#f4f8ff',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    borderColor: '#dfe8f5',
    dividerColor: '#e8eef7',
    borderRadius: '10px',
    borderRadiusSmall: '8px',
  },
  Card: {
    borderRadius: '14px',
    color: '#ffffff',
    borderColor: 'transparent',
    paddingMedium: '20px 20px',
    titleFontSizeMedium: '15px',
    titleFontWeight: '650',
  },
  Button: {
    borderRadiusMedium: '8px',
    borderRadiusSmall: '7px',
    fontWeight: '600',
  },
  Input: {
    borderRadius: '9px',
    color: '#ffffff',
    border: '1px solid #e4e8ef',
  },
  InternalSelection: {
    borderRadius: '9px',
    border: '1px solid #e4e8ef',
  },
  DataTable: {
    borderRadius: '12px',
    thColor: '#f7f9fc',
    thTextColor: '#62707e',
    thFontWeight: '650',
    tdColorHover: '#f8faff',
    borderColor: '#eef1f6',
    tdTextColor: '#3f4b56',
  },
  Menu: {
    itemColorHover: '#ffffff',
    itemColorActive: '#dbeafe',
    itemColorActiveHover: '#dbeafe',
    itemTextColorActive: '#1d4ed8',
    itemTextColorActiveHover: '#1d4ed8',
    itemTextColor: '#49617f',
    itemTextColorHover: '#1d4ed8',
    itemIconColor: '#7188a8',
    itemIconColorHover: '#2563eb',
    itemIconColorActive: '#2563eb',
    itemHeight: '40px',
    borderRadius: '8px',
  },
  Layout: {
    color: '#f4f8ff',
    siderColor: '#eaf3ff',
    headerColor: '#ffffff',
  },
  Tag: {
    borderRadius: '6px',
  },
  Dialog: {
    borderRadius: '15px',
  },
  Pagination: {
    itemBorderRadius: '8px',
  },
}

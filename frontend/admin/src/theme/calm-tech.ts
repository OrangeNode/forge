import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * 「克制科技 · Calm Tech」主题令牌。
 *
 * 依据用户确认的风格提案（frontend/admin/design-showcase.html 第 01 套）：
 * 浅灰底、白色面板、蓝色主色、橙色点睛、克制的圆角与阴影。
 * 只在这里维护颜色与尺寸，页面不写死色值。
 */
export const calmTechTheme: GlobalThemeOverrides = {
  common: {
    fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
    primaryColor: '#536fe8',
    primaryColorHover: '#647ff3',
    primaryColorPressed: '#4459c9',
    primaryColorSuppl: '#647ff3',
    infoColor: '#536fe8',
    successColor: '#43aa7a',
    warningColor: '#f6a94a',
    errorColor: '#e2554a',
    textColorBase: '#202938',
    textColor1: '#17212b',
    textColor2: '#3f4b56',
    textColor3: '#8290a2',
    bodyColor: '#f5f7fb',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    borderColor: '#e8ecf2',
    dividerColor: '#eef0f4',
    borderRadius: '8px',
    borderRadiusSmall: '6px',
  },
  Card: {
    borderRadius: '15px',
    color: '#ffffff',
    borderColor: '#e8ecf2',
    paddingMedium: '18px 18px',
    titleFontSizeMedium: '15px',
    titleFontWeight: '650',
  },
  Button: {
    borderRadiusMedium: '9px',
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
    itemColorActive: '#edf1ff',
    itemColorActiveHover: '#e4eaff',
    itemTextColorActive: '#536fe8',
    itemTextColorActiveHover: '#536fe8',
    itemTextColor: '#8290a2',
    itemIconColorActive: '#536fe8',
    itemHeight: '36px',
    borderRadius: '8px',
  },
  Layout: {
    color: '#f5f7fb',
    siderColor: '#ffffff',
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

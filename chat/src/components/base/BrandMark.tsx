/** 中性 Chat 品牌标识：蓝色圆角气泡 + 三个白点。 */
export const BrandMark = ({ size = 28 }: { size?: number }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 64 64"
    aria-hidden="true"
    focusable="false"
  >
    <rect x="6" y="7" width="52" height="40" rx="18" fill="#4d6bfe" />
    <path d="M22 43h14L19 57l1-14Z" fill="#4d6bfe" />
    <circle cx="23" cy="27" r="4" fill="#fff" />
    <circle cx="32" cy="27" r="4" fill="#fff" />
    <circle cx="41" cy="27" r="4" fill="#fff" />
  </svg>
)

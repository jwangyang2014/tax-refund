export const AUTH_EXPIRED_EVENT = "auth-expired";

export function notifyAuthExpired() {
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
}
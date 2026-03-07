import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

const STORAGE_KEY = 'vortex-filter';

function readLocal<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(`${STORAGE_KEY}-${key}`);
    if (raw == null) return fallback;
    if (typeof fallback === 'number') {
      const n = Number(raw);
      return (Number.isFinite(n) ? n : fallback) as T;
    }
    const s = String(raw).trim();
    return (s !== '' ? s : fallback) as T;
  } catch {
    return fallback;
  }
}

function writeLocal(key: string, value: string | number) {
  try {
    localStorage.setItem(`${STORAGE_KEY}-${key}`, String(value));
  } catch {}
}

// 生效的股东号/股票代码（订单簿、回报流、下单均用此值）；输入框为“待切换”的编辑值，点「切换」后才生效
export const useFilterStore = defineStore('filter', () => {
  const shareholderId = ref<string>(readLocal('shareholderId', 'A001000000'));
  const securityId = ref<string>(readLocal('securityId', '600030'));
  const depth = ref<number>(readLocal('depth', 10));

  // 输入框中正在编辑的值，点「切换」后才会赋给上面两个并驱动页面
  const editShareholderId = ref<string>(shareholderId.value);
  const editSecurityId = ref<string>(securityId.value);

  function applyEdit() {
    const sh = editShareholderId.value.trim();
    const sid = editSecurityId.value.trim();
    if (sh) shareholderId.value = sh;
    if (sid) securityId.value = sid;
    editShareholderId.value = shareholderId.value;
    editSecurityId.value = securityId.value;
  }

  watch(shareholderId, (v) => writeLocal('shareholderId', (v ?? '').trim()));
  watch(securityId, (v) => writeLocal('securityId', (v ?? '').trim()));
  watch(depth, (v) => writeLocal('depth', v ?? 10));

  return { shareholderId, securityId, depth, editShareholderId, editSecurityId, applyEdit };
});

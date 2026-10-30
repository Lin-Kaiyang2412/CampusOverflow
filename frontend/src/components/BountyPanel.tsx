import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import type { Bounty } from '../types';

export function BountyPanel({ questionId, canManage }: { questionId: number; canManage: boolean }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const busy = useRef(false);
  const [bounty, setBounty] = useState<Bounty>();
  const [points, setPoints] = useState(50);
  const [days, setDays] = useState(7);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    let current = true;
    setBounty(undefined);
    api
      .latestBounty(questionId)
      .then((value) => {
        if (current) {
          setBounty(value);
          setError('');
        }
      })
      .catch((e: Error) => {
        if (current) setError(e.message);
      });
    return () => {
      current = false;
    };
  }, [questionId, revision]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const remaining = bounty ? Math.max(0, Math.ceil((Date.parse(bounty.expiresAt) - now) / 1000)) : 0;
  const open = bounty?.status === 'OPEN' && remaining > 0;
  async function submit() {
    if (busy.current) return;
    if (!Number.isInteger(points) || points < 10 || points > 500 || !Number.isInteger(days) || days < 1 || days > 14) {
      setError('积分须为 10–500 的整数，期限须为 1–14 天的整数。');
      return;
    }
    busy.current = true;
    setPending(true);
    setError('');
    try {
      await api.bounty(questionId, points, days);
      dialog.current?.close();
      setRevision((value) => value + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : '悬赏发布失败');
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  return (
    <section aria-label="问题悬赏" className="bounty-panel">
      {bounty && (
        <p>
          {bounty.points} 积分悬赏 ·{' '}
          {open
            ? `剩余 ${Math.floor(remaining / 86400)} 天 ${Math.floor((remaining % 86400) / 3600)} 小时 ${Math.floor((remaining % 3600) / 60)} 分 ${remaining % 60} 秒`
            : bounty.status === 'OPEN'
              ? '已到期，等待结算'
              : `状态：${bounty.status === 'AWARDED' ? '已发放' : bounty.status === 'EXPIRED' ? '已到期' : bounty.status}`}
        </p>
      )}
      {canManage && !open && (
        <button
          type="button"
          onClick={() => {
            setError('');
            dialog.current?.showModal();
          }}
        >
          发起悬赏
        </button>
      )}
      {error && !dialog.current?.open && (
        <p className="error" role="alert">
          悬赏：{error} <button onClick={() => setRevision((value) => value + 1)}>重新加载</button>
        </p>
      )}
      <dialog
        ref={dialog}
        aria-labelledby="bounty-title"
        onCancel={(event) => {
          if (pending) event.preventDefault();
        }}
      >
        <form
          className="form-page"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <h2 id="bounty-title">发起积分悬赏</h2>
          <p className="muted">发布后将冻结相应积分，权限和可用余额由服务器验证。</p>
          <label>
            悬赏积分（10–500）
            <input
              autoFocus
              type="number"
              required
              min={10}
              max={500}
              step={1}
              disabled={pending}
              value={points}
              onChange={(event) => setPoints(Number(event.target.value))}
            />
          </label>
          <label>
            期限（1–14 天）
            <input
              type="number"
              required
              min={1}
              max={14}
              step={1}
              disabled={pending}
              value={days}
              onChange={(event) => setDays(Number(event.target.value))}
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <div className="actions">
            <button type="button" disabled={pending} onClick={() => dialog.current?.close()}>
              取消
            </button>
            <button className="primary" disabled={pending}>
              {pending ? '发布中…' : '确认发布'}
            </button>
          </div>
        </form>
      </dialog>
    </section>
  );
}

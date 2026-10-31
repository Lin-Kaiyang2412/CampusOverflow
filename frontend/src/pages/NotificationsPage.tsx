import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useRemote } from '../components/useRemote';

export function NotificationsPage() {
  const [page, setPage] = useState(1);
  const [pending, setPending] = useState(false);
  const [actionError, setActionError] = useState('');
  const loader = useCallback(() => api.notifications(page), [page]);
  const { data, loading, error, reload } = useRemote(loader);
  const totalPages = data ? Math.max(1, Math.ceil(data.total / Math.max(1, data.size))) : 1;
  return (
    <div className="form-page">
      <h1>通知</h1>
      <button
        disabled={pending || loading || !data?.total}
        onClick={async () => {
          setPending(true);
          setActionError('');
          try {
            await api.readAll();
            window.dispatchEvent(new Event('notifications-read'));
            reload();
          } catch (e) {
            setActionError(e instanceof Error ? e.message : '标记失败');
          } finally {
            setPending(false);
          }
        }}
      >
        {pending ? '正在标记…' : '全部标记为已读'}
      </button>
      {loading && <p role="status">正在加载通知…</p>}
      {(error || actionError) && (
        <p className="error" role="alert">
          {error || actionError} <button onClick={reload}>重新加载</button>
        </p>
      )}
      <ul className="plain">
        {data?.items.map((n) => (
          <li key={n.id} className={n.read ? 'muted' : ''}>
            <Link
              to={
                n.link.startsWith('/') && !n.link.startsWith('//') && !n.link.includes('\\') ? n.link : '/notifications'
              }
            >
              {n.title}
            </Link>
            {!n.read && <span className="badge">未读</span>}
            <span className="muted"> · {new Date(n.createdAt).toLocaleString('zh-CN')}</span>
          </li>
        ))}
      </ul>
      {!loading && !error && data?.total === 0 && <p className="muted">暂时没有通知。</p>}
      {!loading && !error && data && data.total > 0 && (
        <nav className="pagination" aria-label="通知分页">
          <button disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
            上一页
          </button>
          <span>
            第 {page} / {totalPages} 页
          </span>
          <button disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>
            下一页
          </button>
        </nav>
      )}
    </div>
  );
}

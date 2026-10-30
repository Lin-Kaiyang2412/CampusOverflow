import { useCallback, useState } from 'react';
import { api } from '../api';
import { useRemote } from '../components/useRemote';

const PERIODS = [
  ['week', '本周'],
  ['month', '本月'],
  ['term', '本学期'],
  ['all', '总榜'],
] as const;

export function LeaderboardPage() {
  const [period, setPeriod] = useState<string>('week');
  const loader = useCallback(() => api.leaderboard(period), [period]);
  const { data: rows = [], loading, error, reload } = useRemote(loader);

  return (
    <div className="form-page">
      <h1>声誉排行榜</h1>
      <div className="tabs" aria-label="排行周期">
        {PERIODS.map(([value, label]) => (
          <button
            key={value}
            aria-pressed={period === value}
            className={period === value ? 'active' : ''}
            onClick={() => setPeriod(value)}
          >
            {label}
          </button>
        ))}
      </div>
      <div aria-live="polite">
        {loading && <p>正在加载排行榜…</p>}
        {error && (
          <p className="error" role="alert">
            {error} <button onClick={reload}>重试</button>
          </p>
        )}
      </div>
      <table className="board" aria-label="声誉排行榜" aria-busy={loading}>
        <thead>
          <tr>
            <th>名次</th>
            <th>用户</th>
            <th>声誉</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.userId}>
              <td>{r.rank}</td>
              <td>
                {r.displayName}
                {r.verified && (
                  <span className="verified" title="已认证">
                    ✔
                  </span>
                )}
              </td>
              <td>{r.score}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!loading && !error && rows.length === 0 && <p className="muted">这个周期还没有数据。</p>}
    </div>
  );
}

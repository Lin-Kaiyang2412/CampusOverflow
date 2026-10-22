import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import type { Page, QuestionSummary, SearchResult } from '../types';

export function QuestionListPage() {
  const { me } = useAuth();
  const [data, setData] = useState<Page<QuestionSummary | SearchResult> | null>(null);
  const [tags, setTags] = useState<{ name: string; questionCount: number }[]>([]);
  const [keyword, setKeyword] = useState('');
  const [activeTag, setActiveTag] = useState<string | undefined>();
  const [sort, setSort] = useState('newest');
  const [unanswered, setUnanswered] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [page, setPage] = useState(1);
  const totalPages = data ? Math.max(1, Math.ceil(data.total / Math.max(1, data.size))) : 1;

  function selectTag(tag: string | undefined) {
    setActiveTag(tag);
    setPage(1);
  }

  useEffect(() => {
    api.popularTags().then(setTags).catch(() => undefined);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    setData(null);
    const searching = keyword.trim() !== '' || activeTag || unanswered || sort !== 'newest';
    const promise = searching
      ? api.search(keyword.trim(), { tag: activeTag, sort, unanswered, page })
      : api.questions(page);
    promise.then((r) => {
      if (!cancelled) setData(r);
    }).catch((e: unknown) => {
      if (!cancelled) setError(e instanceof Error ? e.message : '请检查网络后重试');
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [keyword, activeTag, sort, unanswered, retry, page]);

  return (
    <div className="layout">
      <section aria-label="问题列表" aria-busy={loading}>
        <div className="toolbar">
          <input aria-label="搜索问题" placeholder="搜索问题（支持中文全文检索）" value={keyword}
                 onChange={(e) => { setKeyword(e.target.value); setPage(1); }} />
          <select aria-label="问题排序" value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }}>
            <option value="newest">最新</option>
            <option value="active">最近活跃</option>
            <option value="hot">得分最高</option>
            <option value="relevance">相关度</option>
          </select>
          <label className="check">
            <input type="checkbox" checked={unanswered} onChange={(e) => { setUnanswered(e.target.checked); setPage(1); }} />
            仅看未回答
          </label>
          {me && <Link className="primary" to="/ask">我要提问</Link>}
        </div>

        {activeTag && (
          <p className="muted">
            标签筛选：<b>{activeTag}</b> <button onClick={() => selectTag(undefined)}>清除</button>
          </p>
        )}
        {loading && <p className="list-state muted" role="status">正在加载问题…</p>}
        {error && (
          <div className="list-state" role="alert">
            <p className="error">问题加载失败：{error}</p>
            <button onClick={() => setRetry((value) => value + 1)}>重新加载</button>
          </div>
        )}

        <ul className="qlist">
          {data?.items.map((q) => (
            <li key={q.id}>
              <div className="stats">
                <span><b>{q.score}</b> 票</span>
                <span className={q.accepted ? 'accepted' : ''}><b>{q.answerCount}</b> 回答</span>
                <span>{q.viewCount} 浏览</span>
              </div>
              <div className="qmain">
                <Link className="qtitle" to={`/questions/${q.id}`}>{q.title}</Link>
                <p className="excerpt">{q.excerpt}</p>
                <div className="tagrow">
                  {q.tags.map((t) => <button key={t} className="tag" onClick={() => selectTag(t)}>{t}</button>)}
                  <span className="muted">
                    {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>
        {!loading && !error && data?.items.length === 0 && <p className="list-state muted" role="status">没有匹配的问题，换个关键词或筛选条件试试。</p>}
        {!loading && !error && data && data.total > 0 && (
          <nav className="pagination" aria-label="问题分页">
            <button disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span className="muted" role="status">第 {page} / {totalPages} 页 · 共 {data.total} 个问题</span>
            <button disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </nav>
        )}
      </section>

      <aside>
        <h3>热门标签</h3>
        <div className="tagrow">
          {tags.map((t) => (
            <button key={t.name} className="tag" onClick={() => selectTag(t.name)}>
              {t.name}<span className="count">{t.questionCount}</span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}

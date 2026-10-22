import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { MarkdownEditor } from '../components/MarkdownEditor';
import { BountyPanel } from '../components/BountyPanel';
import type { QuestionDetail } from '../types';

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const { me } = useAuth();
  const [q, setQ] = useState<QuestionDetail | null>(null);
  const [related, setRelated] = useState<{ id: number; title: string }[]>([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  const load = useCallback(() => {
    api.question(questionId).then((value) => { setQ(value); setError(''); }).catch((e) => setError(e.message));
  }, [questionId]);

  useEffect(() => {
    let current = true;
    setQ(null); setRelated([]); setError(''); setDraft('');
    if (!Number.isSafeInteger(questionId) || questionId < 1) { setError('问题编号无效。'); return; }
    api.question(questionId).then((value) => { if (current) setQ(value); }).catch((e: Error) => { if (current) setError(e.message); });
    api.related(questionId).then((value) => { if (current) setRelated(value); }).catch(() => undefined);
    return () => { current = false; };
  }, [load, questionId]);

  const act = async (fn: () => Promise<unknown>) => {
    if (pending) return;
    setPending(true); setError('');
    try { await fn(); load(); } catch (e) { setError(e instanceof Error ? e.message : '操作失败'); }
    finally { setPending(false); }
  };

  if (!q) return <div role={error ? 'alert' : 'status'}><p>{error || '加载中…'}</p>{error && <button onClick={load}>重新加载</button>}</div>;

  const canManage = q.permissions.canManage;

  return (
    <div className="layout">
      <article>
        <h1>{q.title}</h1>
        <p className="muted">
          {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览
          {q.course && <> · 课程 {q.course.name}</>}
        </p>

        <div className="post">
          <div className="votebox">
            <button disabled={!me || pending} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === 1 ? 0 : 1))} aria-label="赞同">▲</button>
            <b>{q.score}</b>
            <button disabled={!me || pending} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === -1 ? 0 : -1))} aria-label="反对">▼</button>
          </div>
          <div className="body">
            {/* 正文由后端渲染并经 commonmark + OWASP Sanitizer 双重净化 */}
            <div dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
            <div className="tagrow">{q.tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>
            <CommentList comments={q.comments} targetType="QUESTION" targetId={q.id} onDone={load} canComment={!!me} />
            <BountyPanel key={q.id} questionId={q.id} canManage={canManage} />
          </div>
        </div>

        <h2>{q.answerCount} 个回答</h2>
        {q.answers.map((a) => (
          <div className={`post ${q.acceptedAnswerId === a.id ? 'is-accepted' : ''}`} key={a.id} id={`answer-${a.id}`}>
            <div className="votebox">
              <button disabled={!me || pending} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === 1 ? 0 : 1))} aria-label="赞同">▲</button>
              <b>{a.score}</b>
              <button disabled={!me || pending} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === -1 ? 0 : -1))} aria-label="反对">▼</button>
              {q.acceptedAnswerId === a.id && <span className="check-mark" title="已采纳">✔</span>}
            </div>
            <div className="body">
              <div dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
              <p className="muted">
                {a.author.displayName}
                {a.endorsedBy && <span className="endorsed">教师认证 · {a.endorsedBy.displayName}</span>}
                · {new Date(a.createdAt).toLocaleString('zh-CN')}
              </p>
              <div className="actions">
                {q.permissions.canAccept && !q.acceptedAnswerId &&
                  <button disabled={pending} onClick={() => act(() => api.accept(q.id, a.id))}>采纳此回答</button>}
                {canManage && !a.endorsedBy && <button disabled={pending} onClick={() => act(() => api.endorse(a.id))}>教师认证</button>}
              </div>
              <CommentList comments={a.comments} targetType="ANSWER" targetId={a.id} onDone={load} canComment={!!me} />
            </div>
          </div>
        ))}

        {me ? (
          <section className="answer-form">
            <h3>写回答</h3>
            <MarkdownEditor value={draft} onChange={setDraft} disabled={pending} />
            <button className="primary" disabled={pending || !draft.trim()} onClick={() => act(async () => {
              await api.answer(q.id, draft);
              setDraft('');
            })}>提交回答
            </button>
          </section>
        ) : <p className="muted"><Link to="/login">登录</Link>后可以回答与投票。</p>}
        {error && <p role="alert" className="error">{error}</p>}
      </article>

      <aside>
        <h3>相关问题</h3>
        <ul className="plain">
          {related.map((r) => <li key={r.id}><Link to={`/questions/${r.id}`}>{r.title}</Link></li>)}
          {related.length === 0 && <li className="muted">暂无</li>}
        </ul>
      </aside>
    </div>
  );
}

function CommentList({ comments, targetType, targetId, onDone, canComment }: {
  comments: { id: number; author: { displayName: string }; body: string; createdAt: string }[];
  targetType: 'QUESTION' | 'ANSWER';
  targetId: number;
  onDone: () => void;
  canComment: boolean;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  return (
    <div className="comments">
      {error && <p className="error" role="alert">{error}</p>}
      {comments.map((c) => (
        <div className="comment" key={c.id}>
          <span>{c.body}</span>
          <span className="muted"> — {c.author.displayName}</span>
        </div>
      ))}
      {canComment && (open ? (
        <div className="comment-form">
          <input aria-label="评论内容" maxLength={600} disabled={pending} value={text} onChange={(e) => setText(e.target.value)} placeholder="评论，可用 @昵称 提醒他人" />
          <button disabled={pending || !text.trim()} onClick={async () => {
            setPending(true); setError('');
            try { await api.comment(targetType, targetId, text.trim()); setText(''); setOpen(false); onDone(); }
            catch (e) { setError(e instanceof Error ? e.message : '评论发布失败'); }
            finally { setPending(false); }
          }}>
            发表
          </button>
        </div>
      ) : <button className="link" onClick={() => setOpen(true)}>添加评论</button>)}
    </div>
  );
}

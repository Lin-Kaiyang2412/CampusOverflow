import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MarkdownEditor } from '../components/MarkdownEditor';
import { api } from '../api';
import { useAuth } from '../auth';

export function AskPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [tags, setTags] = useState('');
  const [courseId, setCourseId] = useState<string>('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const submit = async () => {
    if (submittingRef.current) return;
    const cleanTitle = title.trim();
    const cleanBody = body.trim();
    const cleanTags = [...new Set(tags.split(/[,，\s]+/).filter(Boolean))];
    if (cleanTitle.length < 10 || cleanTitle.length > 150) {
      setError('标题需要 10–150 字，请具体描述你的问题。');
      return;
    }
    if (cleanBody.length < 20) {
      setError('正文至少需要 20 字，可以补充尝试过程和遇到的错误。');
      return;
    }
    if (cleanTags.length < 1 || cleanTags.length > 5) {
      setError('请填写 1–5 个不重复的标签，用空格或逗号分隔。');
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    setError('');
    try {
      const { id } = await api.ask({
        courseId: courseId ? Number(courseId) : undefined,
        title: cleanTitle,
        body: cleanBody,
        tags: cleanTags,
      });
      navigate(`/questions/${id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '发布失败，请稍后重试。');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <form
      className="form-page"
      aria-busy={submitting}
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h1>提出一个问题</h1>
      <label>
        标题（10–150 字，尽量具体）
        <input
          required
          minLength={10}
          maxLength={150}
          disabled={submitting}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </label>
      <p className="muted">正文至少 20 字：说明你尝试过什么、卡在哪里。</p>
      <MarkdownEditor value={body} onChange={setBody} disabled={submitting} minLength={20} />
      <label>
        标签（1–5 个，用空格或逗号分隔）
        <input
          required
          disabled={submitting}
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          placeholder="数据结构 红黑树"
        />
      </label>
      <label>
        所属课程（可选）
        <select disabled={submitting} value={courseId} onChange={(e) => setCourseId(e.target.value)}>
          <option value="">不关联课程</option>
          {me?.courses.map((c) => (
            <option key={c.id} value={c.id}>
              {c.code} {c.name}
            </option>
          ))}
        </select>
      </label>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? '正在发布…' : '发布问题'}
      </button>
    </form>
  );
}

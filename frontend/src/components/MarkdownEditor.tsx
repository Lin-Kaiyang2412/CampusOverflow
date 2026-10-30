import { Fragment, useId, type ReactNode } from 'react';

// 只产生 React 文本节点，不接受原始 HTML 或脚本链接。
function inline(text: string): ReactNode {
  return text
    .split(/(`[^`]+`|\*\*[^*]+\*\*)/g)
    .map((part, index) =>
      part.startsWith('`') && part.endsWith('`') ? (
        <code key={index}>{part.slice(1, -1)}</code>
      ) : part.startsWith('**') && part.endsWith('**') ? (
        <strong key={index}>{part.slice(2, -2)}</strong>
      ) : (
        <Fragment key={index}>{part}</Fragment>
      ),
    );
}
function highlight(code: string): ReactNode {
  return code
    .split(
      /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b(?:const|let|var|function|return|if|else|class|public|private|static|void|import|from|export|true|false|null|def|for|while|print|new|async|await)\b|\b\d+\b)/g,
    )
    .map((token, index) => (
      <span
        key={index}
        className={
          /^["']/.test(token)
            ? 'syntax-string'
            : /^\d+$/.test(token)
              ? 'syntax-number'
              : /^(const|let|var|function|return|if|else|class|public|private|static|void|import|from|export|true|false|null|def|for|while|print|new|async|await)$/.test(
                    token,
                  )
                ? 'syntax-keyword'
                : undefined
        }
      >
        {token}
      </span>
    ));
}
function Preview({ text }: { text: string }) {
  const result: ReactNode[] = [];
  let prose: string[] = [];
  let code: string[] | null = null;
  const flush = () => {
    if (prose.length) {
      result.push(<Fragment key={result.length}>{blocks(prose.join('\n'))}</Fragment>);
      prose = [];
    }
  };
  for (const line of text.split('\n')) {
    if (code !== null) {
      if (/^```\s*$/.test(line)) {
        result.push(
          <pre key={result.length}>
            <code>{highlight(code.join('\n'))}</code>
          </pre>,
        );
        code = null;
      } else code.push(line);
    } else if (/^```/.test(line)) {
      flush();
      code = [];
    } else prose.push(line);
  }
  flush();
  if (code !== null)
    result.push(
      <pre key={result.length}>
        <code>{highlight(code.join('\n'))}</code>
      </pre>,
    );
  return <>{result}</>;
}
function blocks(text: string) {
  return text.split('\n').map((line, index) => {
    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    if (heading) {
      const content = inline(heading[2]);
      return heading[1].length === 1 ? <h2 key={index}>{content}</h2> : <h3 key={index}>{content}</h3>;
    }
    if (/^[-*] /.test(line))
      return (
        <p className="preview-list-item" key={index}>
          • {inline(line.slice(2))}
        </p>
      );
    if (line.startsWith('> ')) return <blockquote key={index}>{inline(line.slice(2))}</blockquote>;
    return <p key={index}>{inline(line) || '\u00a0'}</p>;
  });
}
export function MarkdownEditor({
  value,
  onChange,
  disabled = false,
  minLength,
}: {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  minLength?: number;
}) {
  const id = useId();
  return (
    <div className="markdown-editor">
      <label htmlFor={id}>正文（Markdown）</label>
      <p className="muted" id={`${id}-help`}>
        预览支持标题、粗体、行内代码、代码块、引用和无序列表；最终排版以发布后的服务端渲染为准。
      </p>
      <div className="editor-columns">
        <textarea
          id={id}
          aria-describedby={`${id}-help`}
          required
          minLength={minLength}
          maxLength={30000}
          rows={12}
          disabled={disabled}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="描述你的思路，代码用三反引号包裹"
        />
        <section className="markdown-preview body" aria-label="Markdown 实时预览">
          <strong>实时预览</strong>
          {value ? <Preview text={value} /> : <p className="muted">输入正文后在这里预览。</p>}
        </section>
      </div>
    </div>
  );
}

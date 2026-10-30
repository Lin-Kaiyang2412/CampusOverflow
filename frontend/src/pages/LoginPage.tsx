import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [form, setForm] = useState({
    username: '',
    password: '',
    displayName: '',
    email: '',
    college: '',
    className: '',
  });
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  const submit = async () => {
    if (pending) return;
    setPending(true);
    setError('');
    try {
      if (mode === 'register') {
        await api.register(form);
      }
      await login(form.username, form.password);
      navigate('/');
    } catch (e) {
      setError(e instanceof Error ? e.message : '操作失败');
    } finally {
      setPending(false);
    }
  };

  return (
    <form
      className="form-page narrow"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <div className="tabs">
        <button
          type="button"
          disabled={pending}
          className={mode === 'login' ? 'active' : ''}
          onClick={() => {
            setMode('login');
            setError('');
          }}
        >
          登录
        </button>
        <button
          type="button"
          disabled={pending}
          className={mode === 'register' ? 'active' : ''}
          onClick={() => {
            setMode('register');
            setError('');
          }}
        >
          注册
        </button>
      </div>
      <label>
        学号 / 工号
        <input
          required
          autoComplete="username"
          minLength={mode === 'register' ? 4 : undefined}
          maxLength={20}
          disabled={pending}
          value={form.username}
          onChange={set('username')}
        />
      </label>
      <label>
        密码
        <input
          required
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          minLength={mode === 'register' ? 8 : undefined}
          maxLength={64}
          disabled={pending}
          type="password"
          value={form.password}
          onChange={set('password')}
        />
      </label>
      {mode === 'register' && (
        <>
          <label>
            昵称
            <input
              required
              minLength={2}
              maxLength={20}
              disabled={pending}
              value={form.displayName}
              onChange={set('displayName')}
            />
          </label>
          <label>
            校园邮箱
            <input
              required
              type="email"
              maxLength={100}
              disabled={pending}
              value={form.email}
              onChange={set('email')}
            />
          </label>
          <label>
            学院
            <input maxLength={50} disabled={pending} value={form.college} onChange={set('college')} />
          </label>
          <label>
            班级
            <input maxLength={50} disabled={pending} value={form.className} onChange={set('className')} />
          </label>
        </>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button type="submit" disabled={pending} className="primary">
        {pending ? '正在处理…' : mode === 'login' ? '登录' : '注册并登录'}
      </button>
    </form>
  );
}

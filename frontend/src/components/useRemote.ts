import { useCallback, useEffect, useState } from 'react';

export function useRemote<T>(loader: () => Promise<T>) {
  const [data, setData] = useState<T>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((value) => value + 1), []);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError('');
    setData(undefined);
    loader()
      .then((value) => {
        if (current) setData(value);
      })
      .catch((e: unknown) => {
        if (current) setError(e instanceof Error ? e.message : '加载失败');
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [loader, revision]);
  return { data, loading, error, reload };
}

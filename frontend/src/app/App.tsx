import { RouterProvider, type RouterProviderProps } from 'react-router';
import { router } from './router';

export function App({
  router: providedRouter = router,
}: {
  router?: RouterProviderProps['router'];
}) {
  return <RouterProvider router={providedRouter} />;
}

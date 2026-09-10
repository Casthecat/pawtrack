import axios from 'axios'
import { QueryClient } from '@tanstack/react-query'
export const queryClient = new QueryClient({ defaultOptions: { queries: { retry: (count, error) => !(axios.isAxiosError(error) && [401, 403].includes(error.response?.status || 0)) && count < 1, staleTime: 5000 } } })

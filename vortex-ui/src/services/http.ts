import axios from 'axios';
import { API_BASE } from './config';

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 10_000,
});

export type ApiResult<T = any> = {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp: number;
};


import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendRoot = dirname(fileURLToPath(import.meta.url));
const assetsRoot = resolve(frontendRoot, 'static', 'assets');
const backend = new URL(process.env.HANARE_BE_URL || 'http://127.0.0.1:8081');
const host = process.env.HANARE_FE_HOST || '127.0.0.1';
const port = Number(process.env.HANARE_FE_PORT || 5173);

if (!['http:', 'https:'].includes(backend.protocol) || !Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('HANARE_BE_URL hoặc HANARE_FE_PORT không hợp lệ.');
}

const types = { '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };

function unavailable(response, error) {
  if (response.headersSent) return response.destroy(error);
  response.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(`Không kết nối được BE tại ${backend.origin}. Hãy chạy BE trước, rồi tải lại trang.`);
}

async function serveAsset(request, response, pathname) {
  const filename = resolve(assetsRoot, pathname.slice('/assets/'.length));
  if (!filename.startsWith(assetsRoot + sep)) {
    response.writeHead(404).end();
    return;
  }
  try {
    if (!(await stat(filename)).isFile()) throw new Error('Not a file');
    response.writeHead(200, {
      'Content-Type': `${types[extname(filename)] || 'application/octet-stream'}; charset=utf-8`,
      'Cache-Control': 'no-cache'
    });
    if (request.method === 'HEAD') return response.end();
    createReadStream(filename).on('error', error => response.destroy(error)).pipe(response);
  } catch {
    response.writeHead(404).end();
  }
}

async function serveWorkspace(request, response) {
  const html = await readFile(resolve(frontendRoot, 'templates', 'app.html'), 'utf8');
  const output = html.replace('th:action="@{/logout}"', 'action="/logout"');
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(request.method === 'HEAD' ? undefined : output);
}

async function serveLogin(request, response, url) {
  try {
    const backendResponse = await fetch(new URL('/login', backend), {
      redirect: 'manual',
      headers: { Cookie: request.headers.cookie || '' },
      signal: AbortSignal.timeout(15000)
    });
    const cookies = backendResponse.headers.getSetCookie();
    if (backendResponse.status !== 200) {
      const headers = { 'Cache-Control': 'no-store' };
      if (cookies.length) headers['Set-Cookie'] = cookies;
      if (backendResponse.headers.has('location')) headers.Location = backendResponse.headers.get('location');
      response.writeHead(backendResponse.status, headers).end();
      return;
    }
    const backendHtml = await backendResponse.text();
    const csrfInput = backendHtml.match(/<input\b[^>]*\bname=["']_csrf["'][^>]*>/i)?.[0];
    const token = csrfInput?.match(/\bvalue=["']([^"']+)["']/i)?.[1];
    if (!token || !/^[A-Za-z0-9_-]+$/.test(token)) throw new Error('BE không cấp CSRF token cho trang đăng nhập.');

    let html = await readFile(resolve(frontendRoot, 'templates', 'login.html'), 'utf8');
    html = html.replace('th:action="@{/login}"', 'action="/login"');
    html = html.replace('<form method="post" action="/login">',
      `<form method="post" action="/login"><input type="hidden" name="_csrf" value="${token}">`);
    html = html.replace('th:if="${param.error}"', url.searchParams.has('error') ? '' : 'hidden');
    html = html.replace('th:if="${param.logout}"', url.searchParams.has('logout') ? '' : 'hidden');
    const headers = { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' };
    if (cookies.length) headers['Set-Cookie'] = cookies;
    response.writeHead(200, headers).end(request.method === 'HEAD' ? undefined : html);
  } catch (error) {
    unavailable(response, error);
  }
}

async function forward(request, response, url) {
  const target = new URL(url.pathname + url.search, backend);
  const headers = { ...request.headers, host: backend.host };
  delete headers.connection;
  try {
    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request,
      duplex: 'half',
      redirect: 'manual',
      signal: AbortSignal.timeout(30000)
    });
    const outgoing = {};
    for (const [name, value] of upstream.headers) {
      if (!['connection', 'content-encoding', 'content-length', 'set-cookie'].includes(name)) outgoing[name] = value;
    }
    const cookies = upstream.headers.getSetCookie();
    if (cookies.length) outgoing['Set-Cookie'] = cookies;
    const location = outgoing.location;
    if (location?.startsWith(backend.origin)) outgoing.location = location.slice(backend.origin.length) || '/';
    outgoing['Cache-Control'] = 'no-store';
    response.writeHead(upstream.status, outgoing);
    if (request.method === 'HEAD' || !upstream.body) return response.end();
    for await (const chunk of upstream.body) response.write(chunk);
    response.end();
  } catch (error) {
    unavailable(response, error);
  }
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`);
  const path = url.pathname;
  try {
    if (path === '/') {
      response.writeHead(302, { Location: '/projects' }).end();
    } else if (path.startsWith('/assets/') && ['GET', 'HEAD'].includes(request.method)) {
      await serveAsset(request, response, path);
    } else if (path === '/login' && ['GET', 'HEAD'].includes(request.method)) {
      await serveLogin(request, response, url);
    } else if (path === '/api' || path.startsWith('/api/') || path === '/logout' || path === '/login' || path === '/error') {
      await forward(request, response, url);
    } else if ((path === '/projects' || path.startsWith('/projects/')) && ['GET', 'HEAD'].includes(request.method)) {
      await serveWorkspace(request, response);
    } else if (path === '/favicon.ico') {
      response.writeHead(204).end();
    } else {
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Không tìm thấy trang.');
    }
  } catch (error) {
    unavailable(response, error);
  }
});

server.listen(port, host, () => {
  process.stdout.write(`HANARE FE: http://${host}:${port} → BE ${backend.origin}\n`);
});

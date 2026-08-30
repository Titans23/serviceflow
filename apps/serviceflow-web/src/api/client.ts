export type Role = 'GUEST'|'CUSTOMER'|'ADMIN'
const API = '/api'

export function token() { return sessionStorage.getItem('serviceflow_token') }

export function parseSseData(data:string):unknown {
  try { return JSON.parse(data) }
  catch { return data }
}

export async function api<T>(path:string, options:RequestInit={}):Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) headers.set('Content-Type','application/json')
  if (token()) headers.set('Authorization',`Bearer ${token()}`)
  const response = await fetch(API+path,{...options,headers})
  if (!response.ok) { const error = await response.json().catch(()=>({message:response.statusText})); throw new Error(error.message||'请求失败') }
  if (response.status===204) return undefined as T
  return response.json()
}

export async function stream(path:string, body:unknown, onEvent:(name:string,data:any)=>void) {
  const response = await fetch(API+path,{method:'POST',headers:{'Content-Type':'application/json','Authorization':`Bearer ${token()}`},body:JSON.stringify(body)})
  if (!response.ok || !response.body) throw new Error('无法建立客服连接')
  const reader=response.body.getReader(), decoder=new TextDecoder(); let buffer=''
  while(true){ const {done,value}=await reader.read(); if(done) break; buffer+=decoder.decode(value,{stream:true});
    const blocks=buffer.split(/\r?\n\r?\n/); buffer=blocks.pop()||''
    for(const block of blocks){ let name='message', data=''; for(const line of block.split(/\r?\n/)){ if(line.startsWith('event:')) name=line.slice(6).trim(); if(line.startsWith('data:')) data+=line.slice(5).trim() }
      if(data) onEvent(name,name==='token' ? data : parseSseData(data)) }
  }
}

export interface Module { id: string; label: string; path: string; buildTool: string; dependencies: string[] }
export interface Analysis { repository: {owner:string;name:string;url:string;ref?:string;provider?:'github'|'gitlab';shareable?:boolean}; nodes:Module[]; edges:{source:string;target:string}[]; cycles:string[][]; stats:{modules:number;edges:number;externalDependencies:number;cycles:number;filesScanned:number;totalFiles?:number;languages?:Record<string,number>}; warnings:string[] }
export function githubUrl(value: string): boolean { try { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'github.com' && /^\/[\w.-]+\/[\w.-]+\/?$/.test(url.pathname) && !url.search && !url.hash && !url.username && !url.password; } catch { return false; } }
/** Syntax only: the server independently enforces the configured GitLab allowlist. */
export function repositoryUrl(value: string): boolean {
  try {
    const url = new URL(value);
    if (url.hostname === 'github.com') return githubUrl(value);
    return url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash
      && /^\/[\w.-]+(?:\/[\w.-]+)+\/?$/.test(url.pathname);
  } catch { return false; }
}
/** Fail closed if the server does not explicitly mark a public GitHub report shareable. */
export function canShare(data: Analysis): boolean {
  return data.repository.provider === 'github' && data.repository.shareable === true && githubUrl(data.repository.url);
}
export function connections(data: Analysis, id: string): number { return data.edges.filter(e => e.source === id || e.target === id).length; }
export function cycleIds(data: Analysis): Set<string> { return new Set(data.cycles.flat()); }
const modules = ['gateway','identity','catalog','checkout','payments','notifications','search','shared-kernel','observability'];
const pairs = [[0,1],[0,2],[0,3],[0,6],[0,8],[1,7],[1,8],[2,6],[2,7],[3,2],[3,4],[3,5],[3,7],[4,3],[4,7],[5,7],[6,7],[7,8]];
export const demo: Analysis = { repository:{owner:'example',name:'commerce-platform',url:'',ref:'main'}, nodes:modules.map(id=>({id,label:id,path:`modules/${id}/pom.xml`,buildTool:'maven',dependencies:id==='shared-kernel'?['org.slf4j:slf4j-api']:['org.springframework.boot:spring-boot-starter','org.slf4j:slf4j-api']})), edges:pairs.map(([a,b])=>({source:modules[a],target:modules[b]})),cycles:[['checkout','payments']],stats:{modules:9,edges:18,externalDependencies:2,cycles:1,filesScanned:9},warnings:[] };

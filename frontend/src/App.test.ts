// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import App from './App.vue';
import { demo } from './model';
const stubs={DependencyGraph:{template:'<div data-testid="graph-stub" />'}};
let wrappers:ReturnType<typeof mount>[]=[];
function setup(){const wrapper=mount(App,{global:{stubs}});wrappers.push(wrapper);return wrapper;}
beforeEach(()=>{window.history.replaceState({},'', '/');vi.stubGlobal('fetch',vi.fn())});
afterEach(()=>{wrappers.forEach(w=>w.unmount());wrappers=[];vi.unstubAllGlobals()});
describe('analysis interface',()=>{
 it('clearly labels synthetic demo and disables sharing',()=>{const w=setup();expect(w.text()).toContain('Illustrative data — not a live repository');expect(w.find('.demo-badge').text()).toBe('DEMO');expect(w.find('.secondary-button').attributes('disabled')).toBeDefined()});
 it('filters modules and exposes keyboard-accessible details',async()=>{const w=setup();await w.find('[aria-label="Filter modules"]').setValue('identity');expect(w.findAll('.module-item')).toHaveLength(1);await w.find('.module-item').trigger('click');expect(w.find('.detail-panel h2').text()).toBe('identity')});
 it('rejects unsupported URLs before calling the server',async()=>{const w=setup();await w.find('[aria-label="GitHub or configured GitLab repository URL"]').setValue('http://example.com/owner/repo');await w.find('form').trigger('submit');expect(w.find('[role="alert"]').text()).toContain('Enter an HTTPS repository URL');expect(fetch).not.toHaveBeenCalled()});
 it('renders successful analysis, warnings and SHA-pinned sharing URL',async()=>{vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...demo,repository:{owner:'apache',name:'maven',url:'https://github.com/apache/maven',ref:'a'.repeat(40),provider:'github',shareable:true},warnings:['Partial tree: some files were omitted.']})} as Response);const w=setup();await w.find('[aria-label="GitHub or configured GitLab repository URL"]').setValue('https://github.com/apache/maven');await w.find('form').trigger('submit');await flushPromises();expect(w.find('.demo-badge').exists()).toBe(false);expect(w.find('.warning').text()).toContain('Partial tree');expect(window.location.search).toContain('ref='+ 'a'.repeat(40));expect(w.find('.secondary-button').attributes('disabled')).toBeUndefined();expect(w.find('.source-link').attributes('href')).toContain('/blob/'+ 'a'.repeat(40))});
 it('shows backend errors without presenting demo as successful analysis',async()=>{vi.mocked(fetch).mockResolvedValue({ok:false,status:404,json:async()=>({message:'Repository not found'})} as Response);const w=setup();await w.find('[aria-label="GitHub or configured GitLab repository URL"]').setValue('https://github.com/a/b');await w.find('form').trigger('submit');await flushPromises();expect(w.find('[role="alert"]').text()).toContain('Repository not found');expect(w.find('.demo-badge').exists()).toBe(true)});
 it('replays a shared URL using its pinned revision',async()=>{window.history.replaceState({},'', '/?repo=https%3A%2F%2Fgithub.com%2Fa%2Fb&ref=abc123');vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>demo} as Response);setup();await flushPromises();expect(fetch).toHaveBeenCalledWith('/api/analyze',expect.objectContaining({body:JSON.stringify({url:'https://github.com/a/b',ref:'abc123'})}))});
});

const privateResult={...demo,repository:{owner:'secret/group',name:'internal-project',url:'https://gitlab.corp.example/secret/group/internal-project',ref:'secret-sha',provider:'gitlab',shareable:false}};
describe('private analysis protections',()=>{
 it('submits nested GitLab URL only by POST, keeps private metadata out of history and all links',async()=>{
  const writeText=vi.fn();Object.defineProperty(navigator,'clipboard',{value:{writeText},configurable:true});
  vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>privateResult} as Response);
  const w=setup();await w.find('[aria-label="GitHub or configured GitLab repository URL"]').setValue(privateResult.repository.url);
  await w.find('form').trigger('submit');await flushPromises();
  expect(fetch).toHaveBeenCalledWith('/api/analyze',expect.objectContaining({body:JSON.stringify({url:privateResult.repository.url}),cache:'no-store',referrerPolicy:'no-referrer'}));
  expect(w.find('.privacy-notice').text()).toContain('Sharing disabled');expect(w.find('.secondary-button').attributes('disabled')).toBeDefined();
  expect(window.location.search).toBe('');expect(w.find('.source-link').exists()).toBe(false);
  expect(w.findAll('a').every(a=>!a.attributes('href')?.includes('secret'))).toBe(true);
  await w.find('.secondary-button').trigger('click');expect(writeText).not.toHaveBeenCalled();
  expect(localStorage.length).toBe(0);expect(sessionStorage.length).toBe(0);
 });
 it('does not replay private URL parameters and removes them immediately',async()=>{
  window.history.replaceState({},'', '/?repo='+encodeURIComponent(privateResult.repository.url)+'&ref=secret-sha');
  const w=setup();await flushPromises();expect(fetch).not.toHaveBeenCalled();expect(window.location.search).toBe('');
  expect(w.find('[role="alert"]').text()).toContain('cannot be opened from a shared URL');
  expect((w.find('[aria-label="GitHub or configured GitLab repository URL"]').element as HTMLInputElement).value).toBe('');
 });
 it('fails closed when privacy metadata is absent',async()=>{
  vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...demo,repository:{...privateResult.repository,provider:undefined,shareable:undefined}})} as Response);
  const w=setup();await w.find('[aria-label="GitHub or configured GitLab repository URL"]').setValue(privateResult.repository.url);
  await w.find('form').trigger('submit');await flushPromises();expect(w.find('.secondary-button').attributes('disabled')).toBeDefined();expect(w.find('.source-link').exists()).toBe(false);expect(window.location.search).toBe('');
 });
 it('does not expose token fields and explains server-only configuration',()=>{const w=setup();expect(w.find('input[type="password"]').exists()).toBe(false);expect(w.find('.privacy-help').text()).toContain('GITLAB_TOKEN on the server');});
});

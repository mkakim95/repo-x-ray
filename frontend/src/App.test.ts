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
 it('rejects unsupported URLs before calling the server',async()=>{const w=setup();await w.find('[aria-label="Public GitHub repository URL"]').setValue('https://example.com/owner/repo');await w.find('form').trigger('submit');expect(w.find('[role="alert"]').text()).toContain('Enter a full public repository URL');expect(fetch).not.toHaveBeenCalled()});
 it('renders successful analysis, warnings and SHA-pinned sharing URL',async()=>{vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...demo,repository:{owner:'apache',name:'maven',url:'https://github.com/apache/maven',ref:'a'.repeat(40)},warnings:['Partial tree: some files were omitted.']})} as Response);const w=setup();await w.find('[aria-label="Public GitHub repository URL"]').setValue('https://github.com/apache/maven');await w.find('form').trigger('submit');await flushPromises();expect(w.find('.demo-badge').exists()).toBe(false);expect(w.find('.warning').text()).toContain('Partial tree');expect(window.location.search).toContain('ref='+ 'a'.repeat(40));expect(w.find('.secondary-button').attributes('disabled')).toBeUndefined();expect(w.find('.source-link').attributes('href')).toContain('/blob/'+ 'a'.repeat(40))});
 it('shows backend errors without presenting demo as successful analysis',async()=>{vi.mocked(fetch).mockResolvedValue({ok:false,status:404,json:async()=>({message:'Repository not found'})} as Response);const w=setup();await w.find('[aria-label="Public GitHub repository URL"]').setValue('https://github.com/a/b');await w.find('form').trigger('submit');await flushPromises();expect(w.find('[role="alert"]').text()).toContain('Repository not found');expect(w.find('.demo-badge').exists()).toBe(true)});
 it('replays a shared URL using its pinned revision',async()=>{window.history.replaceState({},'', '/?repo=https%3A%2F%2Fgithub.com%2Fa%2Fb&ref=abc123');vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>demo} as Response);setup();await flushPromises();expect(fetch).toHaveBeenCalledWith('/api/analyze',expect.objectContaining({body:JSON.stringify({url:'https://github.com/a/b',ref:'abc123'})}))});
});

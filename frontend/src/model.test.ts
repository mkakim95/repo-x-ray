import { describe, expect, it } from 'vitest';
import { githubUrl, repositoryUrl, canShare, connections, cycleIds, demo } from './model';
describe('repository URLs',()=>{it('accepts public GitHub HTTPS repository paths',()=>{expect(githubUrl('https://github.com/apache/maven')).toBe(true)});it('rejects other hosts, credentials, branches, and non-HTTPS',()=>{for(const value of ['http://github.com/a/b','https://github.com.evil/a/b','https://user@github.com/a/b','https://github.com/a/b/tree/main','not a url'])expect(githubUrl(value)).toBe(false)})});
describe('graph insights',()=>{it('counts both incoming and outgoing links',()=>expect(connections(demo,'gateway')).toBe(5));it('collects all cycle members',()=>expect([...cycleIds(demo)]).toEqual(['checkout','payments']))});

describe('private repository URL syntax and share policy',()=>{
 it('accepts nested GitLab groups on HTTPS including configured base paths',()=>{expect(repositoryUrl('https://gitlab.corp.example/platform/backend/order-service')).toBe(true);expect(repositoryUrl('https://gitlab.corp.example:8443/gitlab/team/repo')).toBe(true)});
 it('rejects credentials, tokens, insecure transport and single-component paths',()=>{for(const value of ['https://token@gitlab.example/a/b','https://gitlab.example/a/b?private_token=secret','http://gitlab.example/a/b','https://gitlab.example/a','https://gitlab.example/a/b#token'])expect(repositoryUrl(value)).toBe(false)});
 it('fails closed for GitLab or unknown privacy metadata even if shareable is true',()=>{for(const repository of [{...demo.repository,provider:'gitlab' as const,shareable:true},{...demo.repository,provider:undefined,shareable:true},{...demo.repository,provider:'github' as const,shareable:undefined}])expect(canShare({...demo,repository})).toBe(false)});
});

import { describe, expect, it } from 'vitest';
import { githubUrl, connections, cycleIds, demo } from './model';
describe('repository URLs',()=>{it('accepts public GitHub HTTPS repository paths',()=>{expect(githubUrl('https://github.com/apache/maven')).toBe(true)});it('rejects other hosts, credentials, branches, and non-HTTPS',()=>{for(const value of ['http://github.com/a/b','https://github.com.evil/a/b','https://user@github.com/a/b','https://github.com/a/b/tree/main','not a url'])expect(githubUrl(value)).toBe(false)})});
describe('graph insights',()=>{it('counts both incoming and outgoing links',()=>expect(connections(demo,'gateway')).toBe(5));it('collects all cycle members',()=>expect([...cycleIds(demo)]).toEqual(['checkout','payments']))});

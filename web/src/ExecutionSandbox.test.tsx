import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ExecutionSandbox } from './ExecutionSandbox';
import type { DecisionGraph, DecisionService, SandboxEvaluation } from './api';

const service:DecisionService={id:'s1',serviceKey:'ELIGIBILITY',name:'Eligibility',description:null,status:'ACTIVE',inputs:[{fieldKey:'score',label:'Credit score',type:{builtinType:'NUMBER',customTypeId:null},required:true}],outputs:[{fieldKey:'decision',label:'Decision',type:{builtinType:'TEXT',customTypeId:null},required:true}],updatedAt:'2026-08-29T00:00:00Z',version:1};
const graph:DecisionGraph={id:'g1',decisionServiceId:'s1',serviceKey:'ELIGIBILITY',serviceName:'Eligibility',validationStatus:'VALID',issues:[],nodes:[],edges:[],updatedAt:'2026-08-29T00:00:00Z',version:4};
const result:SandboxEvaluation={evaluationId:'12345678-1234-1234-1234-123456789abc',decisionServiceId:'s1',graphVersion:4,outputs:{decision:'APPROVE'},decisions:[{nodeId:'n1',nodeKey:'threshold',nodeName:'Threshold',implementation:'EXPRESSION',implementationVersion:2,matchedRuleId:null,value:true},{nodeId:'n2',nodeKey:'outcome',nodeName:'Outcome',implementation:'TABLE',implementationVersion:3,matchedRuleId:'r1',value:'APPROVE'}]};

describe('ExecutionSandbox',()=>{
  afterEach(()=>{cleanup();vi.restoreAllMocks();});
  it('submits typed inputs and renders version-qualified decision results',async()=>{const fetch=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify(result),{status:200,headers:{'Content-Type':'application/json'}}));render(<ExecutionSandbox projectId="p1" service={service} graph={graph} types={[]} onBack={()=>undefined}/>);await userEvent.type(screen.getByLabelText(/Credit score/),'750');await userEvent.click(screen.getByRole('button',{name:'Evaluate saved graph'}));expect(await screen.findByText('APPROVE',{selector:'.output-grid pre'})).toBeInTheDocument();expect(screen.getByText('Threshold')).toBeInTheDocument();expect(screen.getByText('TABLE')).toBeInTheDocument();expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({inputs:{score:750}});});
  it('blocks invalid graphs and validates required input locally',async()=>{const invalid={...graph,validationStatus:'INVALID' as const,issues:[{severity:'ERROR' as const,code:'RC-GRAPH-EDGE-MISSING',message:'Connect the graph.',nodeId:null,portId:null}]};render(<ExecutionSandbox projectId="p1" service={service} graph={invalid} types={[]} onBack={()=>undefined}/>);expect(screen.getByRole('button',{name:'Evaluate saved graph'})).toBeDisabled();expect(screen.getByRole('alert')).toHaveTextContent('not executable');});
});

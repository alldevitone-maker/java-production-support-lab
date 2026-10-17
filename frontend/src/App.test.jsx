import React from 'react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
vi.mock('./api.js',async()=>({isBrowserDemo:true,request:(await import('./demo.js')).request}));
import {request} from './demo.js';
import {App} from './main.jsx';
beforeEach(()=>request('/payments',{method:'DELETE'}));
afterEach(cleanup);
it('loads the catalog, creates an operation and exposes its identifiers',async()=>{
 render(<App/>);await screen.findAllByText('Banco Aurora');
 const submit=screen.getByRole('button',{name:/Simular transferência/});await waitFor(()=>expect(submit.disabled).toBe(false));
 fireEvent.click(submit);const dialog=await screen.findByRole('dialog');
 expect(within(dialog).getByText(/^SIMTX/).textContent).toHaveLength(32);
 expect(within(dialog).getByText(/^E00000000/).textContent).toHaveLength(32);
 expect(within(dialog).getByText('PIX_SETTLED')).toBeTruthy();
 fireEvent.keyDown(document,{key:'Escape'});await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
 expect(screen.getByText('Última resposta:')).toBeTruthy();
});
it('switches pages, language and theme without losing the session',async()=>{
 render(<App/>);await screen.findAllByText('Banco Aurora');
 fireEvent.click(screen.getByRole('button',{name:'Conectores'}));expect(screen.getByText('Cinco instituições. Um contrato de integração.')).toBeTruthy();
 expect(screen.getByText('connector-aurora')).toBeTruthy();
 fireEvent.click(screen.getByRole('button',{name:'Guia SQL'}));expect(screen.getByText('payment_events')).toBeTruthy();
 fireEvent.click(screen.getByRole('button',{name:'Change language'}));expect(screen.getByText('Investigation also happens in SQL.')).toBeTruthy();
 fireEvent.click(screen.getByRole('button',{name:'Light theme'}));expect(document.documentElement.dataset.theme).toBe('light');
});

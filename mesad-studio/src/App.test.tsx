import {beforeEach,describe,expect,it,vi} from 'vitest';
import {fireEvent,render,screen,within} from '@testing-library/react';
import App from './App';
vi.mock('./ClockScene',()=>({default:({engine}:{engine:string})=><div data-testid="clock-scene">{engine} geometry</div>}));

describe('MESAD live studio interactions',()=>{
 beforeEach(()=>localStorage.clear());
 it('navigates between the eight integrated instrument screens',()=>{
  render(<App/>);expect(screen.getByText('A moment, precisely yours.')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button',{name:'World Clock'})[0]);expect(screen.getByText('One planet, many present moments.')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button',{name:'Alarm'})[0]);expect(screen.getByText('Good mornings begin the night before.')).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button',{name:'Clock Studio'})[0]);expect(screen.getByText('Shape your time.')).toBeInTheDocument();
 });
 it('creates a timer and persists it locally',()=>{
  render(<App/>);fireEvent.click(screen.getAllByRole('button',{name:'Timer'})[0]);fireEvent.change(screen.getByLabelText('MINUTES'),{target:{value:'2'}});fireEvent.change(screen.getByPlaceholderText('Name this timer'),{target:{value:'Tea'}});fireEvent.click(screen.getByRole('button',{name:/Create timer/i}));expect(screen.getByText('Tea')).toBeInTheDocument();expect(JSON.parse(localStorage.getItem('mesad-timers')||'[]')[0].duration).toBe(120000);
 });
 it('adds and removes a repeating alarm from the native-like alarm editor',()=>{
  render(<App/>);fireEvent.click(screen.getAllByRole('button',{name:'Alarm'})[0]);fireEvent.click(screen.getByRole('button',{name:/New alarm/i}));const dialog=screen.getByRole('heading',{name:'Set an alarm'}).closest('.alarm-modal') as HTMLElement;fireEvent.change(within(dialog).getByPlaceholderText('Give this moment a name'),{target:{value:'Morning'}});fireEvent.click(within(dialog).getByRole('button',{name:'M'}));fireEvent.click(within(dialog).getByRole('button',{name:'Save alarm'}));expect(screen.getByText('Morning')).toBeInTheDocument();expect(JSON.parse(localStorage.getItem('mesad-alarms')||'[]')).toHaveLength(3);
 });
 it('adds an IANA city and keeps it in local storage',()=>{
  render(<App/>);fireEvent.click(screen.getAllByRole('button',{name:'World Clock'})[0]);fireEvent.change(screen.getByPlaceholderText('Search cities or timezones'),{target:{value:'Tokyo'}});fireEvent.click(screen.getAllByRole('button',{name:/Tokyo/})[0]);expect(screen.getAllByText('Tokyo').length).toBeGreaterThan(0);expect(JSON.parse(localStorage.getItem('mesad-cities')||'[]').some((c:{zone:string})=>c.zone==='Asia/Tokyo')).toBe(true);
 });
});

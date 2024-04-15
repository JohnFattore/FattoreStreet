import { useEffect } from 'react';
import { getOptions } from './axiosFunctions';
import { IOption } from '../interfaces';
import OptionRow from './OptionRow';
import Table from 'react-bootstrap/Table';

export default function OptionTable({ setMessage, options, selections, optionsDispatch, selectionsDispatch, week }) {

  if (options.length == 0) {
    return (<h3 role="noOptions">There are no options for this week</h3>)
  }

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col" role="optionTickerHeader">Ticker</th>
          <th scope="col" role="optionNameHeader">Name</th>
          <th scope="col" role="optionSundayHeader">Beginning of Week [Sunday]</th>
          <th scope="col" role="optionPriceHeader">Current Price</th>
        </tr>
      </thead>
      <tbody>
        {options.map((option: IOption) =>
          <OptionRow option={option} selections={selections} selectionsDispatch={selectionsDispatch} setMessage={setMessage} key={option.id}/>
        )}
      </tbody>
    </Table>
  );
}
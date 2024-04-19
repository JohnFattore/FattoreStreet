import { useEffect } from 'react';
import { getOptions, getSelections } from './axiosFunctions';
import { IOption, ISelection } from '../interfaces';
import OptionSelectionRow from './OptionSelectionRow';
import Table from 'react-bootstrap/Table';

export default function OptionSelectionTable({ setMessage, options, selections, optionsDispatch, selectionsDispatch, week }) {

  if (selections.length == 0) {
    return (<h3 role="noSelections">You haven't made any selections for this week</h3>)
  }

  if (options.length == 0) {
    return (<h3 role="noOptions">There are no options for this week</h3>)
  }

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col" role="optionSelectionTickerHeader">Ticker</th>
          <th scope="col" role="optionSelectionNameHeader">Name</th>
          <th scope="col" role="optionSelectionSundayHeader">Beginning of Week [Sunday]</th>
          <th scope="col" role="optionSelectionStartPriceHeader">Start Price</th>
          <th scope="col" role="optionSelectionPriceHeader">Current Price</th>
          <th scope="col" role="optionSelectionWeeklyChange">Weekly Change</th>
        </tr>
      </thead>
      <tbody>
        {options.map((option: IOption) =>
          <OptionSelectionRow option={option} selections={selections} setMessage={setMessage} key={option.id} />
        )}
      </tbody>
    </Table>
  );
}
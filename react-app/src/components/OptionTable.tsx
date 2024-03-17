import { useEffect } from 'react';
import { getOptions } from './axiosFunctions';
import { IOption } from '../interfaces';
import OptionRow from './OptionRow';
import Table from 'react-bootstrap/Table';

export default function OptionTable({ setMessage, options, selections, optionsDispatch, selectionsDispatch, nextSunday }) {

  let data: IOption[] = []
  useEffect(() => {
    if (options.length == 0) {
      getOptions(nextSunday)
        .then((response) => {
          data = response.data
          for (let i = 0; i < data.length; i++) {
            optionsDispatch({ type: "add", option: data[i] })
          }
        })
        .catch(() => {
          setMessage({ text: "Error", type: "error" })
        })
    }
  }, []);

  if (options.length == 0) {
    return (<h3 role="noOptions">There are no options for this week</h3>)
  }
 
  return (
    <Table>
      <thead>
        <tr>
          <th scope="col" role="tickerHeader">Ticker</th>
          <th scope="col" role="sundayHeader">Beginning of Week [Sunday]</th>
        </tr>
      </thead>
      <tbody>
        {options.map((option: IOption) =>
          <OptionRow option={option} selections={selections} selectionsDispatch={selectionsDispatch} setMessage={setMessage} />
        )}
      </tbody>
    </Table>
  );
}
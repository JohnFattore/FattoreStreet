import Table from 'react-bootstrap/Table';
import { ISelection } from '../interfaces';
import { useEffect } from 'react';
import { getSelections } from '../components/axiosFunctions';
import SelectionRow from './SelectionRow';

export default function SelectionTable({ selections, selectionsDispatch, setMessage, options, week }) {
    // this should go outside the component, so that the table can be reused
    let data: ISelection[] = []
    useEffect(() => {
        if (selections.length == 0) {
            getSelections(week)
                .then((response) => {
                    data = response.data
                    for (let i = 0; i < data.length; i++) {
                        selectionsDispatch({ type: "add", selection: data[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    if (selections.length == 0) {
        return (<h3 role="noSelections">You haven't made any selections for this week</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    <th scope="col" role="tickerHeader">Ticker</th>
                    <th scope="col" role="nameHeader">Ticker</th>
                    <th scope="col" role="sundayHeader">Sunday</th>
                    <th scope="col" role="selectionPriceHeader">Current Price</th>
                </tr>
            </thead>
            <tbody>
                {selections.map((selection: ISelection) => (
                    <SelectionRow selection={selection} setMessage={setMessage} options={options} selectionsDispatch={selectionsDispatch} key={selection.id}/>
                ))}
            </tbody>
        </Table>
    );
}
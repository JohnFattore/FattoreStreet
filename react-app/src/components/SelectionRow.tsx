import { IOption } from '../interfaces';
import { deleteSelection } from './axiosFunctions';
import { useQuote } from './customHooks';

export default function SelectionRow({ selection, setMessage, options, selectionsDispatch }) {
    const filterOptions: IOption[] = options.filter((option: IOption) => option.id == selection.option)
    if (filterOptions.length == 0) {
        return (<tr><td>There was been a mistake, Spike will investigate</td></tr>)
    }
    const option: IOption = filterOptions[0]

    const quote = useQuote(option.ticker, setMessage)

    return (
        <tr>
            <td role="selectionTicker" onClick={() => {
                deleteSelection(selection.id).then(() => {
                    selectionsDispatch({ type: "delete", selection: selection });
                    setMessage({ text: option.ticker + " deleted", type: "success" });
                })
                    .catch((response) => {
                        console.log(response)
                        setMessage({ text: "There was a problem deleting the asset", type: "error" })
                    })
            }}>{option.ticker}</td>
            <td role="selectionSunday">{option.sunday}</td>
            <td role="selectionPrice">${quote.price}</td>
        </tr>
    )
}
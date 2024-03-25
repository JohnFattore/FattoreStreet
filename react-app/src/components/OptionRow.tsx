import { ISelection } from '../interfaces';
import { postSelection } from './axiosFunctions';
import { useQuote } from './customHooks';
import { selectedOption } from './helperFunctions';

export default function OptionRow({ option, selections, selectionsDispatch, setMessage }) {

    const quote = useQuote(option.ticker, setMessage)

    return (
        <tr key={option.id}>
            <td role="optionTicker" style={{ backgroundColor: selectedOption(option, selections) }} onClick={() => {
                const existingSelection = selections.filter((selection: ISelection) => selection.option == option.id)
                if (selections.length < 3 && existingSelection.length == 0 ) {
                    postSelection({
                        option: option.id, 
                        user: 1,
                        id: 1
                    }).then((response) => {
                        const selection: ISelection = {
                            option: option.id, 
                            user: 1,
                            id: response.data.id
                        }
                        selectionsDispatch({type: "add", selection: selection});
                        setMessage({ text: option.ticker + " added", type: "success" })
                    })
                        .catch(() => {
                            setMessage({ text: "There was a problem adding the selection", type: "error" })
                        })
                }
                else {
                    setMessage({ text: "You can only have 3 unqiue selections per week", type: "error" })
                }

            }}>{option.ticker}</td>
            <td role="optionSunday" style={{ backgroundColor: selectedOption(option, selections) }}>{option.sunday}</td>
        <td role="optionPrice" style={{ backgroundColor: selectedOption(option, selections) }}>${quote.price}</td>
        </tr>
    )
}
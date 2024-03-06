import { ISelection } from '../interfaces';
import { postSelection } from './AxiosFunctions';

export default function OptionRow({ option, selections, selectionsDispatch, setMessage }) {
    return (
        <tr key={option.id}>
            <td role="optionTicker" onClick={() => {
                const existingSelection = selections.filter((selection: ISelection) => selection.option == option.id)
                if (selections.length < 3 && existingSelection.length == 0 ) {
                    postSelection({
                        option: option.id, 
                        sunday: option.sunday,
                        user: 1,
                        id: 1
                    }).then((response) => {
                        const selection: ISelection = {
                            option: option.id, 
                            sunday: option.sunday,
                            user: 1,
                            id: response.data.id
                        }
                        selectionsDispatch({type: "add", selection: selection});
                        setMessage({ text: option.ticker + " added", type: "success" })
                    })
                        .catch(() => {
                            setMessage({ text: "There was a problem deleting the selection", type: "error" })
                        })
                }
                else {
                    setMessage({ text: "You can only have 3 unqiue selections per week", type: "error" })
                }

            }}>{option.ticker}</td>
            <td role="optionSunday">{option.sunday}</td>
        </tr>
    )
}
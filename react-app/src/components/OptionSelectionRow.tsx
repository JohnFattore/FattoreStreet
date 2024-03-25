import { useQuote } from './customHooks';
import { selectedOption } from './helperFunctions';

export default function OptionSelectionRow({option, selections, setMessage}) {

    const quote = useQuote(option.ticker, setMessage)

    return (
        <tr key={option.id}>
            <td role="optionSelectionTicker" style={{ backgroundColor: selectedOption(option, selections) }}>{option.ticker}</td>
            <td role="optionSelectionSunday" style={{ backgroundColor: selectedOption(option, selections) }}>{option.sunday}</td>
            <td role="optionSelectionPrice" style={{ backgroundColor: selectedOption(option, selections) }}>${quote.price}</td>        
        </tr>
    )
}
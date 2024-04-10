import { useQuote } from './customHooks';
import { selectedOption } from './helperFunctions';

export default function OptionSelectionRow({option, selections, setMessage}) {

    const quote = useQuote(option.ticker, setMessage)
    const weeklyChange = 100 * (quote.price - option.startPrice)/ option.startPrice
    return (
        <tr key={option.id}>
            <td role="optionSelectionTicker" style={{ backgroundColor: selectedOption(option, selections) }}>{option.ticker}</td>
            <td role="optionSelectionName" style={{ backgroundColor: selectedOption(option, selections) }}>{option.name}</td>
            <td role="optionSelectionSunday" style={{ backgroundColor: selectedOption(option, selections) }}>{option.sunday}</td>
            <td role="optionSelectionStartPrice" style={{ backgroundColor: selectedOption(option, selections) }}>${option.startPrice}</td>
            <td role="optionSelectionPrice" style={{ backgroundColor: selectedOption(option, selections) }}>${quote.price}</td>
            <td role="optionSelectionCurrentChange" style={{ backgroundColor: selectedOption(option, selections) }}>{weeklyChange.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}%</td>                
        </tr>
    )
}
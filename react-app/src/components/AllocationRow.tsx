import { useQuote } from "./helperFunctions";

export default function AllocationRow({ allocation, setMessage }) {
    const quote = useQuote(allocation.ticker, setMessage)

    return (
        <tr>
            <td role='ticker'>{allocation.ticker}</td>
            <td role='shares'>{allocation.shares.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td role='price'>${(allocation.shares * quote.price).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
        </tr>
    );
}
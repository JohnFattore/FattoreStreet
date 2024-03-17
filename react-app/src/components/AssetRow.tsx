import { deleteAsset } from './axiosFunctions';
import { useQuote } from './customHooks';

export default function AssetRow({ asset, setMessage, dispatch }) {
    const quote = useQuote(asset.ticker, setMessage)

    const totalCostBasis = asset.costbasis * asset.shares;
    const totalMarketPrice = quote.price * asset.shares;
    const totalPercentChange = (totalMarketPrice - totalCostBasis) / totalCostBasis * 100

    var strColor = "red";
    if (totalPercentChange > 0) {
        strColor = "green";
    }

    return (
        <tr key={asset.id}>
            <td role="ticker">{asset.ticker}</td>
            <td role="shares">{asset.shares}</td>
            <td role="costbasis">${asset.costbasis}</td>
            <td>${totalCostBasis.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${totalMarketPrice.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{ color: strColor }}>{(totalPercentChange).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}%</td>
            <td role="buy">{asset.buy}</td>
            <td onClick={() => {
                deleteAsset(asset.id).then(() => {
                    dispatch({type: "delete", asset: asset});
                    setMessage({ text: asset.ticker + " deleted", type: "success" })
                })
                    .catch(() => {
                        setMessage({ text: "There was a problem deleting the asset", type: "error" })
                    })
            }}>DELETE</td>
        </tr>
    )
}
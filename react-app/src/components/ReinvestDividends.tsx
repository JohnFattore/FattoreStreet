export default function ReinvestDividends() {

    return (
        <>
            <p>
                The cost basis price is adjusted for dividends and stock splits in order to accuratly calculate a historical return.
                Stocks, for example, often pay out dividends or experience stock splits.
                A 2-1 stock split would cut the price of the stock in half, but leave the market cap unchanged. Past stock prices can also be cut in half to accomidate this.
                A dividend payout decreases the value of the stock by roughly the amount of the dividend, but the shareholder receieves this value as cash.
                Often this dividend is reinvested, granting the shareholder new shares. However, to make historical comparison easier, it is better to decrease the initial cost basis.
                Both accomidation methods leave the amount of shares unchanged, ensuring historical comparison is accurate using just share price.
                </p>
        </>
    )
}
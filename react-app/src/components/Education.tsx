import { Table, Accordion } from "react-bootstrap";
import { IBrokerage } from '../interfaces';

export default function Education() {

    const brockerageHeaders = ["Brokerage Company", "Money Market Fund", "US Mutual Fund", "International Mutual Fund"]
    const taxAccountHeaders = ["Investment Account", "Initial Investment", "Cash Out"]

    const brokerages: IBrokerage[] = [{ "name": "Fidelity", "mmf": "SPAXX", "us": "FSKAX", "inter": "FTIHX", "id": 1 },
    { "name": "Charles Schwab", "mmf": "SNVXX", "us": "SWTSX", "inter": "SWISX", "id": 2 },
    { "name": "Vanguard", "mmf": "VMFXX", "us": "VTSAX", "inter": "VTIAX", "id": 3 }]

    const brokerageRows = brokerages.map((brockerage) =>  (
    <tr key={brockerage.id}>
        {["name", "mmf", "us", "inter"].map((property) => (
            <td key={property}>{brokerages[property]}</td>
        ))}
    </tr>))

const taxAccounts = [{ "name": "Regular Taxable Brokerage Account", "initialInvest": "Income Tax", "cashOut": "Capital Gains Tax", "id": 1 },
{ "name": "Pre-Tax: Individual IRA/401k", "initialInvest": "No Tax", "cashOut": "Income Tax", "id": 2 },
{ "name": "Post-Tax: Roth IRA/401k", "initialInvest": "Income Tax", "cashOut": "No Tax", "id": 3 }]

const taxAccountRows = taxAccounts.map((taxAccount) =>  (
    <tr key={taxAccount.id}>
        {["name", "initialInvest", "cashOut"].map((property) => (
            <td key={property}>{taxAccounts[property]}</td>
        ))}
    </tr>))

return (
    <>
        <h1>Fattore's Investing Principles</h1>
        <p>
            The goal of Fattore Street is to get all workers of America to secure their financial future.
            Automatically dollar cost averaging into funds that track diverse indexes such as the S&P 500 provides workers with a hands off approach to grow their money.
            Taking advantage of taxable accounts such as 401k and Roth IRA keeps more money in your pocket come retirement.
        </p>

        <Accordion defaultActiveKey="0">
            <Accordion.Item eventKey="6">
                <Accordion.Header>Brokerage Accounts</Accordion.Header>
                <Accordion.Body>
                    Fidelity, Schwab, and Vanguard are three of the top choices for brokerage accounts. Below are the passively managed mutual funds for each brokerage that together make up the entire global stock market.
                    <Table>
                        <thead>
                            <tr>
                                {brockerageHeaders.map((property) => (
                                    <th key={property}>{property}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {brokerageRows}
                        </tbody>
                    </Table>
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="2">
                <Accordion.Header>Managing Bad Debt</Accordion.Header>
                <Accordion.Body>
                    Any debt which has an interest rate greater than your money market fund should be paid off.
                    Other debt, notably low interest rate mortgages, can be managed by just paying the monthly premium.
                    Keeping the cash with your emergency fund in a MMF might make more financal sense.
                    Air on the side of paying off debts, paying off debt is a guaranteed return.
                    Once bills are paid, emergency funds established, and bad debt settled, excess cash can be invested.
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="3">
                <Accordion.Header>6 Month Emergency Fund</Accordion.Header>
                <Accordion.Body>
                    Maintain a six-month emergency fund in a money market fund.
                    Money market funds are preferred because they offer the highest return out of all risk free options all with the same brokerage account as your other investments.
                    These funds invest in short-term treasury bonds and are currently yielding around 5% as of August 17, 2024.
                    A high yields savings account is another good option, but might require opening up a separate account.
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="5">
                <Accordion.Header>Buy and Hold</Accordion.Header>
                <Accordion.Body>
                    Investing in low cost, passively managed, market cap weighted, broadly diversified index funds is the best investing strategy. Limiting the number of funds in your portfolio reduces complexity.
                    Allocate 20-40% to the international fund.
                    Focus on staying invested rather than trying to time the market.
                    Over the long term, holding your investments smooths out the ups and downs, leading to a solid average return.
                    $100 invested at the start of the year in 1963 would be worth $41,024.18 at the end of 2023, which is a 40,924.18% return on investment or 10.37% a year *.
                    On the flip side, some years experience 30-40% loses; you need a long time horizon to guarantee proper results.
                    *Reinvesting dividends and not adjusting for inflation.
                    <p><a target="_blank" rel="noopener noreferrer" href="https://www.officialdata.org/us/stocks/s-p-500/1963?amount=100&endYear=2023">www.officialdata.org</a></p>
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="8">
                <Accordion.Header>Picking Investments</Accordion.Header>
                <Accordion.Body>
                    Buying and holding any low cost broad index fund will yield exceptional results.
                    Ensure the fund passively tracks a broad index and has a minimal expense ratio, less than 0.10%.
                    Even professional active fund managers are unable to beat the returns of simple index funds, particularly because of their steep expense ratios.
                    For 401ks, where options are limited, the target date funds are typically a slam dunk.
                    Please stay away from timing the market and especially day trading.
                    Time in the market trumps timing the market and day trading should be considered gambling.
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="10">
                <Accordion.Header>Diversification</Accordion.Header>
                <Accordion.Body>
                    A well diversified portfolio reduces some risks of investing by averaging out the noise of the market.
                    A large index such as the S&P 500 is less volatile compared to the individual stocks that comprise it.
                    The two recommended funds together represent the entire global stock market, ensuring maximum diversification.
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="11">
                <Accordion.Header>Continuous Investing</Accordion.Header>
                <Accordion.Body>
                    Continuous automatic investing helps smooth out buying price and overall returns.
                    Mutual funds are recommended over ETFs because they allow flexible automatic investing.
                    Setting up weekly automatic investing is the ideal way to seamlessly build wealth over time.
                </Accordion.Body>
            </Accordion.Item>
            <Accordion.Item eventKey="9">
                <Accordion.Header>Tax-Advantaged Accounts</Accordion.Header>
                <Accordion.Body>
                    Capitalize on all tax advantaged investment accounts such as IRAs and 401ks.
                    Effectively, investments in IRAs and 401ks are not subject to capital gains tax; the difference is when income tax is applied.
                    These accounts are either government or employer sponsored; the employer 401k match is financially potent.
                    <Table>
                        <thead>
                            <tr>
                                {taxAccountHeaders.map((property) => (
                                    <th key={property}>{property}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {taxAccountRows}
                        </tbody>
                    </Table>
                </Accordion.Body>
            </Accordion.Item>
        </Accordion>
    </>
);
}
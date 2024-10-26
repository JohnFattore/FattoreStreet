import DjangoTable from '../components/DjangoTable';
import Spike from '../components/SpikeImg';
import ExternalLinks from '../components/ExternalLinks';
import { IAlbum, IRatio } from '../interfaces';
import Fees from '../components/Fees';

export default function Entertainment() {

    // this could be saved in database if desired
    const albums: IAlbum[] = [
        {
            name: "Flower Boy",
            artist: "Tyler, The Creator",
            year: 2017,
        },
        {
            name: "2014 Forest Hill Drive",
            artist: "J Cole",
            year: 2014,
        },
        {
            name: "Nevermind",
            artist: "Nirvana",
            year: 1991,
        },
        {
            name: "Songs in the Key of Life",
            artist: "Stevie Wonder",
            year: 1974,
        },
        {
            name: "My Beautiful Dark Twisted Fantasy",
            artist: "Kanye West",
            year: 2010,
        },
        {
            name: "The Blueprint",
            artist: "JAY-Z",
            year: 2001,
        },
        {
            name: "Discovery",
            artist: "Daft Punk",
            year: 2000,
        },
        {
            name: "Currents",
            artist: "Tame Impala",
            year: 2015,
        },
        {
            name: "The Miseducation of Lauryn Hill",
            artist: "Lauryn Hill",
            year: 1998,
        },
        {
            name: "Rumours",
            artist: "Fleetwood Mac",
            year: 1977,
        }
    ]

    const ratios: IRatio[] = [
        {
            name: "Price To Earnings",
            formula: "Market Capitalization / Annual Income",
            description: "Valuation: measures the price investors pay for income. A high PE ratio can indicate that a company is overvalued.",
        },
        {
            name: "Debt Ratio",
            formula: "Total Debt / Total Assets",
            description: "Debt: Measures the ability of a company to pay its debts. A high debt ratio would mean a risker business with less room for shareholders",
        },
        {
            name: "Debt to Equity Ratio",
            formula: "Total Debt / Total Equity",
            description: "Debt: Measures the firms 'leverage'. A high debt to equity ratio would indicate a company that heavily relies on debt as financing.",
        },
        {
            name: "Current Ratio",
            formula: "Current Debt / Current Assets",
            description: "Debt: Measures the companies ability to make short term debt payments. A current ratio over 1 would indicate a company is essentially insolvent / unable to make debt payments.",
        },
        {
            name: "Gross Margin",
            formula: "(Revenue - COGS) / Revenue",
            description: "Profitability: % of revenue that is profit, accounting only for cost of goods sold. An efficient business has a high gross margin",
        },
        {
            name: "Net Margin",
            formula: "(Revenue - (COGS + Expenses + Taxes)) / Revenue",
            description: "Profitability: % of revenue that is profit, accounting for all expenses.",
        },
        {
            name: "Dividend Yield",
            formula: "Annual Dividend Per Share / Share Price",
            description: "Like a bond, this is the income made on the principle. A share with a $100 share price and an annual dividend of $5 would have a 5% dividend yield",
        },
        {
            name: "Dividend Payout Ratio",
            formula: "Total Dividends / Net Income",
            description: "Percent of companies profits they give to their investors",
        },
    ]

    const fields = {
        name: {name: "Name", type: "text"},
        artist: {name: "Artist", type: "text"},
        year: {name: "Year", type: "text"}
    }


    const fields2 = {
        name: {name: "Name", type: "text"},
        formula: {name: "Formula", type: "text"},
        description: {name: "Description", type: "text"}
    }

    return (
        <>
            <h3>Useful Links</h3>
            <ExternalLinks />
            <h3>Fees are costing me in my 401k</h3>
            <Fees/>
            <h3>Notable Company Ratios</h3>
            <DjangoTable setMessage={console.log} models={ratios} dispatch={console.log} fields={fields2} />
            <h1>10 10/10 Albums</h1>
            <DjangoTable setMessage={console.log} models={albums} dispatch={console.log} fields={fields} />
            <Spike />
        </>
    );
}
import { useEffect } from 'react';
import { IOption, IResult } from '../interfaces';
import { getResults } from '../components/axiosFunctions';
import Table from 'react-bootstrap/Table';
import ResultRow from './ResultRow';

export default function ResultTable({ setMessage, results, resultsDispatch }) {
    let data: IOption[] = []
    useEffect(() => {
        if (results.length == 0) {
            getResults()
                .then((response) => {
                    data = response.data
                    for (let i = 0; i < data.length; i++) {
                        resultsDispatch({ type: "add", result: data[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    if (results.length == 0) {
        return (<h3 role="noResults">There are no results for wallstreet</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    <th scope="col" role="resultSunday">Sunday</th>
                    <th scope="col" role="resultPercentChange">User's Portfolio Percent Change</th>
                    <th scope="col" role="SnP500PercentChange">S&P 500 Percent Change</th>
                    <th scope="col" role="russell2000PercentChange">Russell 2000 Percent Change</th>
                    <th scope="col" role="worldEconomyPercentChange">World Economy Percent Change</th>
                    <th scope="col" role="evenAllocation">Even Allocation</th>

                </tr>
            </thead>
            <tbody>
                {results.map((result: IResult) =>
                    <ResultRow result={result} setMessage={setMessage} />
                )}
            </tbody>
        </Table>
    );
}
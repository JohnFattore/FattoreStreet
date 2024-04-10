import { useEffect, useState } from "react";
import { getOptions } from "./axiosFunctions";
import { IOption } from "../interfaces";


export default function ResultRow({ result, setMessage }) {

    const [benchmarks, setBenchmarks] = useState<IOption[]>([]);

    useEffect(() => {
        getOptions(-1, "true")
            .then((response) => {
                setBenchmarks(response.data);
            })
            .catch(() => {
                setMessage({ text: "Error", type: "error" })
            })
    }, []);

    if (benchmarks.length == 0) {
        return (<h3 role='noAssets'>You don't own any assets</h3>)
    }

    console.log(benchmarks)

    let SnP500PC = 0
    let russel2000PC = 0
    let worldEconomyPC = 0
    let evenAllocation = 0	

    for (let i = 0; i < benchmarks.length; i++) {
        if (benchmarks[i].ticker == 'SPY')
            SnP500PC = benchmarks[i].percentChange
        else if (benchmarks[i].ticker == 'VTWO')
            russel2000PC = benchmarks[i].percentChange
        else if (benchmarks[i].ticker == 'VT')
            worldEconomyPC = benchmarks[i].percentChange
        else if (benchmarks[i].name == 'Even Allocation')
            evenAllocation = benchmarks[i].percentChange
    }

    return (
        <tr key={result.id}>
            <td>{result.sunday}</td>
            <td>{result.portfolioPercentChange}%</td>
            <td>{SnP500PC}%</td>
            <td>{russel2000PC}%</td>
            <td>{worldEconomyPC}%</td>
            <td>{evenAllocation}%</td>
        </tr>
    )
}
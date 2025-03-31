import { formatString } from './helperFunctions';
import { useDispatch } from "react-redux";
import { AppDispatch } from '../main';
import { setAsset } from '../reducers/assetReducer';
import { useNavigate } from "react-router-dom";
import { Button } from 'react-bootstrap';

export default function AssetRow({ asset, fields }) {
    const navigate = useNavigate();
    const dispatch = useDispatch<AppDispatch>();
    let tableData: JSX.Element[] = [];

    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["name"] == 'Ticker') {
            tableData.push(<td key={i} onClick={() => {
                dispatch(setAsset(asset))
                navigate("/asset")
            }}>{formatString(asset[fields[i]["field"]], fields[i]["type"])}</td>)
        }
        else if (fields[i]["type"] == "assetView") {
            tableData.push(<td key={i}> <Button
                onClick={() => {
                    dispatch(setAsset(asset))
                    navigate("/asset")
                }}>{`View ${asset[fields[i]["field"]]}`}</Button> </td>)
        }
        else {
            tableData.push(<td key={i}>{formatString(asset[fields[i]["field"]], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr>
            {tableData}
        </tr>)
}
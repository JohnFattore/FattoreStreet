import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { patchOutliers } from './axiosFunctions';
import { handleError } from './helperFunctions';

interface IFormInput {
    notes: string,
    ticker: string,
}

export default function OutlierUpdateForm({ setMessage, dispatch, outliers }) {
    const schema = yup.object().shape({
        notes: yup.string().required(),
        ticker: yup.string().required(),
    });

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        setMessage({ text: "Loading", type: "loading"})
        try {
            var outlier = outliers.find(obj => {
                return obj.ticker === data.ticker
            })
            outlier.notes = data.notes
            patchOutliers(data.notes, outlier.id).then(() => {
                dispatch({ type: "update", outlier: outlier });
                setMessage({ text: data.ticker.concat(" notes updated"), type: "success" })
            }).catch((error) => {
                handleError(error, setMessage)
                //setMessage({ text: "Error setting notes, not logged in", type: "error" })
            })
        }
        catch (error) {
            setMessage({ text: "Error setting notes, ticker isn't in list", type: "error" })
        }
    }
    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("ticker", {
                        required: true
                    })} placeholder='Ticker' />
                    {errors.ticker && <Alert variant="danger" role="tickerError">Error: Ticker field is required</Alert>}

                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("notes", {
                        required: true
                    })} placeholder='Notes' />
                    {errors.notes && <Alert variant="danger" role="notesError">Error: Notes field is required</Alert>}
                </Col>
            </Row>
            <Button type="submit">Update Notes</Button>
        </Form>
    );
}
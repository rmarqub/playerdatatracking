package com.playerdatatracking.operations.manualdata;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.playerdatatracking.clients.PlayerDataClient;
import com.playerdatatracking.common.Constants;
import com.playerdatatracking.common.Methods;
import com.playerdatatracking.entities.indexaldata.ManualTrackedPlayer;
import com.playerdatatracking.exceptions.db.PlayerDataDBException;
import com.playerdatatracking.responses.GenericResponse;


@Component
public class XslImport {
	
	
private GenericResponse response = new GenericResponse();


	private PlayerDataClient pdClient;
	
	// Formatear la fecha para imprimirla
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
	
    private ResourceLoader resourceLoader;

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
	

	public void setPdClient(PlayerDataClient pdClient) {
		this.pdClient = pdClient;
	}
	
	
	public GenericResponse ejecutar(String filePath) throws PlayerDataDBException {
            Resource resource = resourceLoader.getResource(filePath);
            try (FileInputStream fis = new FileInputStream(resource.getFile().getPath()); 
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();

            // Saltar la primera fila si tiene encabezados
            if (iterator.hasNext()) {
                iterator.next();
            }

            while (iterator.hasNext()) {
                Row row = iterator.next();
                String name = row.getCell(0).getStringCellValue();
                ManualTrackedPlayer isAlreadyInDB = pdClient.getPlayerbyName(name);
                if (isAlreadyInDB==null) {
                	ManualTrackedPlayer player = new ManualTrackedPlayer();
                	player.setNombre(name);
                	try {
                		player.setNota((float) row.getCell(1).getNumericCellValue());
                	} catch (Exception e) {
                		break;
                	}
                	try {
                		player.setClub(row.getCell(2).getStringCellValue());
                	} catch (Exception e) {
                		break;
                	}
                	try {
                    	player.setPosicion(row.getCell(3).getStringCellValue());
                	} catch (Exception e) {
                		player.setPosicion("");
                	}
                	try {
                    	player.setMostLikeDestination(row.getCell(5).getStringCellValue());
                	} catch (Exception e) {
                		player.setMostLikeDestination("not clear");
                	}
                	try {
                    	player.setLikeable(row.getCell(6).getStringCellValue());
                	} catch (Exception e) {
                		player.setLikeable("not clear");
                	}
                	try {
                		List<String> qualities = Arrays.asList(row.getCell(4).getStringCellValue().split("\\s*,\\s*"));
                		player.setQualities(qualities);
                	} catch (Exception e) {
                		List<String> qualities = new ArrayList<String>();
                		player.setQualities(qualities);
                	}
                	try {
                		Date fechaInsercion = row.getCell(7).getDateCellValue();
                		player.setDate(fechaInsercion);
                	} catch (Exception e){
                		player.setDate(new Date());
                	}
                	pdClient.savePlayer(player);
                }
            }
            response.setCODE(Constants.CODE_OK);
            response.setDescription("OK");
            return response;

        } catch (IOException e) {
            response.setCODE(Methods.exceptionCodeManagement(e));
            response.setDescription(e.getClass().getSimpleName() + "[]: " + e.getMessage());
            return response;
        }
    }
}
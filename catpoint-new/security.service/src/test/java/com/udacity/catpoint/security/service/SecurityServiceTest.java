package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import io.netty.handler.codec.string.LineSeparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
//
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    private SecurityService securityService;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private BufferedImage bufferedImage;

    @Mock
    private Sensor sensor;


    @BeforeEach
    void init(){
        sensor = mock(Sensor.class);
        securityService = new SecurityService(securityRepository,imageService);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME","ARMED_AWAY"})
    @DisplayName("If alarm is armed and a sensor becomes activated, put the system into pending alarm status")
    public void changeSensorActivationStatus_whenArmedAndSensorActivated_setsPendingAlarm(ArmingStatus armingStatus){

        when(sensor.getActive()).thenReturn(false);

        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);

    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME","ARMED_AWAY"})
    @DisplayName("If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.")
    public void changeSensorActivationStatus_whenArmedAndPendingAndSensorActivated_setsAlarm(ArmingStatus armingStatus){
        when(sensor.getActive()).thenReturn(false);


        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);

    }

    @ParameterizedTest
    @EnumSource(value = SensorType.class, names = {"DOOR","WINDOW","MOTION"})
    @DisplayName("If pending alarm and all sensors are inactive, return to no alarm state.")
    public void changeSensorActivationStatus_whenPendingAndAllSensorsInactive_setsNoAlarm(SensorType sensorType){
        sensor.setSensorType(sensorType);
        when(sensor.getActive()).thenReturn(true);


        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor,false);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @MethodSource("requirement_4_Arguments")
    @DisplayName("If alarm is active, change in sensor state should not affect the alarm state.")
    public void changeSensorActivationStatus_whenAlarmActive_doesNotChangeAlarm(SensorType sensorType, boolean sensorActivity){
        sensor.setSensorType(sensorType);
        when(sensor.getActive()).thenReturn(sensorActivity);

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor,!sensorActivity);

        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));

    }

    public static Stream<Arguments> requirement_4_Arguments() {
        return Stream.of(
                Arguments.of(SensorType.WINDOW,true),
                Arguments.of(SensorType.WINDOW,false),
                Arguments.of(SensorType.DOOR,true),
                Arguments.of(SensorType.DOOR,false),
                Arguments.of(SensorType.MOTION,true),
                Arguments.of(SensorType.MOTION,false)
        );
    }

    @ParameterizedTest
    @EnumSource(value = SensorType.class, names = {"DOOR","WINDOW","MOTION"})
    @DisplayName("If a sensor is activated while already active and the system is in pending state," +
            " change it to alarm state.")
    public void changeSensorActivationStatus_whenPendingAndSensorAlreadyActive_setsAlarm(SensorType sensorType){
        sensor.setSensorType(sensorType);
        when(sensor.getActive()).thenReturn(true);

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }


    @ParameterizedTest
    @EnumSource(value = SensorType.class, names = {"DOOR","WINDOW","MOTION"})
    @DisplayName("If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    public void changeSensorActivationStatus_whenSensorAlreadyInactive_doesNotChangeAlarm(SensorType sensorType){
        sensor.setSensorType(sensorType);
        when(sensor.getActive()).thenReturn(false);

        securityService.changeSensorActivationStatus(sensor,false);

        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));

    }

    @Test
    @DisplayName("If the image service identifies an image containing a cat while the system is armed-home," +
            " put the system into alarm status.")
    public void processImage_whenCatDetectedAndArmedHome_setsAlarm(){
        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(bufferedImage);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);

    }

    @ParameterizedTest
    @EnumSource(value = SensorType.class, names = {"DOOR","WINDOW","MOTION"})
    @DisplayName("If the image service identifies an image that does not contain a cat," +
            " change the status to no alarm as long as the sensors are not active.")
    public void processImage_whenNoCatAndAllSensorsInactive_setsNoAlarm(SensorType sensorType)
    {
        sensor.setSensorType(sensorType);
        sensor.setActive(false);


        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(false);

        securityService.processImage(bufferedImage);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);

    }

    @Test
    @DisplayName("If the system is disarmed, set the status to no alarm.")
    public void setArmingStatus_whenDisarmed_setsNoAlarm(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }


    @ParameterizedTest
    @MethodSource("requirement_10_Arguments")
    @DisplayName("If the system is armed, reset all sensors to inactive.")
    public void setArmingStatus_whenArmed_resetsAllSensorsToInactive(Set<Sensor> sensor_set,ArmingStatus armingStatus){
        when(securityRepository.getSensors()).thenReturn(sensor_set);

        securityService.setArmingStatus(armingStatus);
        securityService.getSensors().forEach(s -> assertFalse(s.getActive()));
    }

    public static Stream<Arguments> requirement_10_Arguments() {

        Sensor sensor_1= new Sensor("Sensor Under Test1", SensorType.WINDOW);
        Sensor sensor_2= new Sensor("Sensor Under Test2", SensorType.DOOR);
        sensor_1.setActive(true);
        sensor_2.setActive(false);
        Set<Sensor> sensor_set = Set.of(sensor_1,sensor_2);
        return Stream.of(
                Arguments.of(sensor_set,ArmingStatus.ARMED_AWAY),
                Arguments.of(sensor_set,ArmingStatus.ARMED_HOME));
    }

    @Test
    @DisplayName("If the system is armed-home while the camera shows a cat," +
            " set the alarm status to alarm.")
    public void setArmingStatus_whenCatPreviouslyDetectedAndArmedHome_setsAlarm(){
        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(true);

        securityService.processImage(bufferedImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);

    }



    //Extra

    @Test
    @DisplayName("If cat leaves and any sensor is active, alarm status should not change")
    public void processImage_whenCatLeavesAndAnySensorIsActive_keepsAlarm() {

        Sensor activeSensor = new Sensor("Sensor Under Test", SensorType.MOTION);
        activeSensor.setActive(true);
        Set<Sensor> sensors = Set.of(activeSensor);


        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);


        securityService.processImage(bufferedImage);


        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);


        reset(securityRepository);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);


        securityService.processImage(bufferedImage);


        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }



    @Test
    public void sensorInActive_SensorBecomesActiveAndArmingStatusIsDisarmed_noChanges(){
        Sensor sensor = new Sensor("Sensor Under Test",SensorType.WINDOW);
        sensor.setActive(false);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));

    }


}
